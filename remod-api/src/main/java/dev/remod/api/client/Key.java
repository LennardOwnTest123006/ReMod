package dev.remod.api.client;

/**
 * Keyboard keys a keybind can default to.
 *
 * <p>The numbers are GLFW key codes, which Minecraft has used since 1.13 and
 * which the version adapter maps to LWJGL2 codes for older builds. Mods refer
 * to keys by name, so that mapping is not their problem.</p>
 */
public enum Key {

    UNKNOWN(-1),
    SPACE(32),
    APOSTROPHE(39),
    COMMA(44),
    MINUS(45),
    PERIOD(46),
    SLASH(47),
    NUM_0(48), NUM_1(49), NUM_2(50), NUM_3(51), NUM_4(52),
    NUM_5(53), NUM_6(54), NUM_7(55), NUM_8(56), NUM_9(57),
    SEMICOLON(59),
    EQUAL(61),
    A(65), B(66), C(67), D(68), E(69), F(70), G(71), H(72), I(73),
    J(74), K(75), L(76), M(77), N(78), O(79), P(80), Q(81), R(82),
    S(83), T(84), U(85), V(86), W(87), X(88), Y(89), Z(90),
    LEFT_BRACKET(91),
    BACKSLASH(92),
    RIGHT_BRACKET(93),
    GRAVE_ACCENT(96),
    ESCAPE(256),
    ENTER(257),
    TAB(258),
    BACKSPACE(259),
    INSERT(260),
    DELETE(261),
    RIGHT(262), LEFT(263), DOWN(264), UP(265),
    PAGE_UP(266), PAGE_DOWN(267),
    HOME(268), END(269),
    F1(290), F2(291), F3(292), F4(293), F5(294), F6(295),
    F7(296), F8(297), F9(298), F10(299), F11(300), F12(301),
    LEFT_SHIFT(340), LEFT_CONTROL(341), LEFT_ALT(342),
    RIGHT_SHIFT(344), RIGHT_CONTROL(345), RIGHT_ALT(346);

    private final int glfwCode;

    Key(int glfwCode) {
        this.glfwCode = glfwCode;
    }

    public int glfwCode() {
        return glfwCode;
    }

    public static Key byGlfwCode(int code) {
        for (Key key : values()) {
            if (key.glfwCode == code) {
                return key;
            }
        }
        return UNKNOWN;
    }
}
