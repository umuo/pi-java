package works.earendil.pi.ai.model;

public sealed interface ToolChoice permits ToolChoice.Auto, ToolChoice.None, ToolChoice.Required, ToolChoice.Named {
    String wireName();

    record Auto() implements ToolChoice {
        @Override
        public String wireName() {
            return "auto";
        }
    }

    record None() implements ToolChoice {
        @Override
        public String wireName() {
            return "none";
        }
    }

    record Required() implements ToolChoice {
        @Override
        public String wireName() {
            return "required";
        }
    }

    record Named(String name) implements ToolChoice {
        public Named {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Tool choice name must not be blank");
            }
        }

        @Override
        public String wireName() {
            return name;
        }
    }
}
