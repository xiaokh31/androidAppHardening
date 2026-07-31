package ah.runtime.bootstrap;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import java.util.regex.Pattern;

final class StartupMetadata {
    static final String ORIGINAL_APPLICATION = "ah.runtime.original_application";
    static final String ORIGINAL_FACTORY = "ah.runtime.original_app_component_factory";
    static final String HAS_ORIGINAL_FACTORY = "ah.runtime.has_original_app_component_factory";
    static final String CONTAINER_ASSET = "ah.runtime.container_asset";
    static final String CONTAINER_MAJOR = "ah.runtime.container_major";
    static final String SIGNER_POLICY_VERSION = "ah.runtime.signer_policy_version";
    static final String RISK_POLICY_VERSION = "ah.runtime.risk_policy_version";

    static final String EXPECTED_CONTAINER_ASSET = "assets/ah/runtime/payload.ahdc";
    private static final Pattern CLASS_NAME =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    final String originalApplication;
    final String originalFactory;
    final boolean hasOriginalFactory;

    private StartupMetadata(
            String originalApplication,
            String originalFactory,
            boolean hasOriginalFactory) {
        this.originalApplication = originalApplication;
        this.originalFactory = originalFactory;
        this.hasOriginalFactory = hasOriginalFactory;
    }

    @SuppressWarnings("deprecation")
    static StartupMetadata read(ApplicationInfo applicationInfo) {
        Bundle metadata = applicationInfo == null ? null : applicationInfo.metaData;
        if (metadata == null) {
            throw invalid("Framework metadata Bundle is absent");
        }

        String application = requiredClassName(metadata, ORIGINAL_APPLICATION);
        Object hasFactoryValue = metadata.get(HAS_ORIGINAL_FACTORY);
        if (!(hasFactoryValue instanceof Boolean)) {
            throw invalid("original factory presence flag has the wrong type");
        }
        boolean hasFactory = (Boolean) hasFactoryValue;
        Object factoryValue = metadata.get(ORIGINAL_FACTORY);
        String factory = null;
        if (hasFactory) {
            if (!(factoryValue instanceof String)) {
                throw invalid("original factory name is missing or has the wrong type");
            }
            factory = validateClassName((String) factoryValue, ORIGINAL_FACTORY);
            if (ShellAppComponentFactory.class.getName().equals(factory)) {
                throw invalid("original factory recursively names the shell factory");
            }
        } else if (metadata.containsKey(ORIGINAL_FACTORY)) {
            throw invalid("original factory name is present while its flag is false");
        }

        requireExactString(metadata, CONTAINER_ASSET, EXPECTED_CONTAINER_ASSET);
        requireExactInteger(metadata, CONTAINER_MAJOR, 1);
        requireExactInteger(metadata, SIGNER_POLICY_VERSION, 1);
        requireExactInteger(metadata, RISK_POLICY_VERSION, 1);
        return new StartupMetadata(application, factory, hasFactory);
    }

    private static String requiredClassName(Bundle metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof String)) {
            throw invalid(key + " is missing or has the wrong type");
        }
        return validateClassName((String) value, key);
    }

    private static String validateClassName(String value, String key) {
        if (value.length() > 255 || !CLASS_NAME.matcher(value).matches()) {
            throw invalid(key + " is not a canonical Java class name");
        }
        return value;
    }

    private static void requireExactString(Bundle metadata, String key, String expected) {
        Object value = metadata.get(key);
        if (!(value instanceof String) || !expected.equals(value)) {
            throw invalid(key + " is missing, mistyped, or unsupported");
        }
    }

    private static void requireExactInteger(Bundle metadata, String key, int expected) {
        Object value = metadata.get(key);
        if (!(value instanceof Integer) || ((Integer) value) != expected) {
            throw invalid(key + " is missing, mistyped, or unsupported");
        }
    }

    private static IllegalStateException invalid(String detail) {
        return PocFailure.create(PocFailure.METADATA_CODE, detail);
    }
}
