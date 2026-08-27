package dev.remod.transform.hook;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.transform.load.GameTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injects a callback into whichever Minecraft class owns the command dispatcher.
 *
 * <h2>Finding an obfuscated class without mappings</h2>
 *
 * <p>Minecraft's {@code Commands} class is called something unpredictable in a
 * stock jar, and the name changes every release. But it holds a field of type
 * {@code com.mojang.brigadier.CommandDispatcher}, and Brigadier is a separate
 * Mojang library that ships <em>unobfuscated</em> -- its class names survive
 * intact.</p>
 *
 * <p>So this transformer does not look for a name at all. It looks for the
 * class that declares a field of that type, which identifies the command class
 * on any version, obfuscated or not, with no mapping file involved.</p>
 *
 * <h2>What is injected</h2>
 *
 * <p>At every {@code return} in the constructor:</p>
 *
 * <pre>
 *   ReModHooks.onCommandDispatcher(this.&lt;dispatcherField&gt;);
 * </pre>
 *
 * <p>The end of the constructor is the right moment because Minecraft registers
 * all of its own commands there, so by the time the hook fires the dispatcher
 * is fully built and ReMod's commands are added on top rather than being
 * overwritten.</p>
 */
public final class CommandDispatcherTransformer implements GameTransformer {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Transform");

    /** The unobfuscated Brigadier type that gives the command class away. */
    public static final String BRIGADIER_DISPATCHER =
            "Lcom/mojang/brigadier/CommandDispatcher;";

    private static final String HOOKS_INTERNAL_NAME =
            "dev/remod/transform/hook/ReModHooks";
    private static final String HOOK_METHOD = "onCommandDispatcher";
    private static final String HOOK_DESCRIPTOR = "(Ljava/lang/Object;)V";

    private final String dispatcherDescriptor;
    private volatile String hookedClass;

    public CommandDispatcherTransformer() {
        this(BRIGADIER_DISPATCHER);
    }

    /**
     * @param dispatcherDescriptor the field descriptor that identifies the
     *                             command class; overridable so the mechanism
     *                             can be tested without Brigadier present
     */
    public CommandDispatcherTransformer(String dispatcherDescriptor) {
        this.dispatcherDescriptor = dispatcherDescriptor;
    }

    @Override
    public String name() {
        return "command-dispatcher-hook";
    }

    /**
     * Every game class is a candidate, because the target cannot be named.
     *
     * <p>Once one class has been hooked the rest are waved through, so the cost
     * is one ASM parse per class until the command class turns up and none
     * after.</p>
     */
    @Override
    public boolean handles(String internalName) {
        return hookedClass == null;
    }

    @Override
    public byte[] transform(String internalName, byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, ClassReader.SKIP_FRAMES);

        FieldNode dispatcherField = findDispatcherField(node);
        if (dispatcherField == null) {
            return original;
        }
        // Static holders and the dispatcher class itself are not what we want:
        // the target holds it as instance state assigned in its constructor.
        if ((dispatcherField.access & Opcodes.ACC_STATIC) != 0) {
            return original;
        }

        int injected = 0;
        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)) {
                continue;
            }
            injected += injectAtReturns(node, method, dispatcherField);
        }
        if (injected == 0) {
            LOG.debug(() -> "Class " + internalName + " holds a dispatcher but has no"
                    + " constructor to hook; leaving it unchanged");
            return original;
        }

        hookedClass = internalName;
        LOG.info("Hooked Minecraft's command class (" + internalName + "."
                + dispatcherField.name + ") at " + injected + " return site(s)");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private FieldNode findDispatcherField(ClassNode node) {
        for (FieldNode field : node.fields) {
            if (dispatcherDescriptor.equals(field.desc)) {
                return field;
            }
        }
        return null;
    }

    /** Adds the hook call before each {@code return}, leaving the stack untouched. */
    private int injectAtReturns(ClassNode node, MethodNode method, FieldNode field) {
        int injected = 0;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != Opcodes.RETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, field.name, field.desc));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_INTERNAL_NAME,
                    HOOK_METHOD, HOOK_DESCRIPTOR, false));
            method.instructions.insertBefore(instruction, hook);
            injected++;
        }
        return injected;
    }

    /** The class that was hooked, or {@code null} when none has been found yet. */
    public String hookedClass() {
        return hookedClass;
    }

    /** Forgets the hooked class so the transformer can be reused. Used by tests. */
    public void reset() {
        hookedClass = null;
    }
}
