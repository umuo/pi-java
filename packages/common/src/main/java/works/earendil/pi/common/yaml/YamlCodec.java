package works.earendil.pi.common.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.util.Map;

public final class YamlCodec {
    private static final Yaml YAML;

    static {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Representer representer = new Representer(options);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        YAML = new Yaml(new SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()), representer, options);
    }

    private YamlCodec() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(String yaml) {
        Object loaded = YAML.load(yaml == null ? "" : yaml);
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("YAML root must be a map");
        }
        return (Map<String, Object>) map;
    }

    public static String stringify(Object value) {
        return YAML.dump(value);
    }
}
