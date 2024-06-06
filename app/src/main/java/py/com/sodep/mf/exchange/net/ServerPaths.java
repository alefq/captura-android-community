package py.com.sodep.mf.exchange.net;

/**
 * This class is used in {@link ServerConnection} to generate the URIs to the
 * MF's services
 *
 * @author Miguel
 */
class ServerPaths {

    private static final String APPLICATION_LIST = "/api/metadata/applications";

    private static final String FORM_LIST = "/api/metadata/forms";

    private static final String FORM_DEFINITION_PATTERN = "/api/metadata/formDefinition/{formId}/{version}";

    private static final String PING = "/api/public/ping";

    private static final String SETTINGS = "/api/public/mobile/defaultSettings";

    private static final String DEVICE_VERIFICATION = "/api/authentication/verification";

    private static final String LOGIN = "/api/authentication/login";
    private static final String ACTIVATION = "/api/authentication/activation";

    private static final String LOOKUP_DATA = "/api/lookupTable/lookupTableDataFast";

    private static final String LOOKUP_DEFINITION_PATTERN = "/api/lookupTable/definition/{lookup}";

    private static final String UPLOAD_HANDLE_PATTERN = "/api/document/upload/handle?formId={formId}&documentId={documentId}&deviceId={deviceId}&size={size}";

    private static final String UPLOAD_STATUS_PATTERN = "/api/document/upload/status?handle=";

    private static final String UPLOAD_FILE_PATTERN = "/api/document/upload/file?handle=";

    static String applicationListPath() {
        return APPLICATION_LIST;
    }

    static String formListPath() {
        return FORM_LIST;
    }

    static String formDefinitionPath(long formId, long version) {
        return FORM_DEFINITION_PATTERN.replace("{formId}", Long.toString(formId)).replace("{version}",
                Long.toString(version));
    }

    static String pingPath() {
        return PING;
    }

    static String settingsPath() {
        return SETTINGS;
    }

    static String deviceVerificationPath() {
        return DEVICE_VERIFICATION;
    }

    static String login() {
        return LOGIN;
    }

    static String lookupData() {
        return LOOKUP_DATA;
    }

    static String lookupDefinition(Long lookupTable) {
        return LOOKUP_DEFINITION_PATTERN.replace("{lookup}", Long.toString(lookupTable));
    }

    static String uploadHandle(Long formId, String documentId, String deviceId, long size) {
        return UPLOAD_HANDLE_PATTERN.replace("{formId}", formId.toString()).
                replace("{documentId}", documentId).replace("{deviceId}", deviceId).replace("{size}", Long.toString(size));
    }

    public static String uploadStatus(String handle) {
        return UPLOAD_STATUS_PATTERN + handle;
    }


    public static String uploadFile(String handle) {
        return UPLOAD_FILE_PATTERN + handle;
    }
}
