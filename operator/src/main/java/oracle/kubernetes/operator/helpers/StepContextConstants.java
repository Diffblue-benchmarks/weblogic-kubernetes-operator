package oracle.kubernetes.operator.helpers;

public interface StepContextConstants {

  static final String SECRETS_VOLUME = "weblogic-credentials-volume";
  static final String SCRIPTS_VOLUME = "weblogic-domain-cm-volume";
  static final String SIT_CONFIG_MAP_VOLUME_SUFFIX = "-weblogic-domain-introspect-cm-volume";
  static final String STORAGE_VOLUME = "weblogic-domain-storage-volume";
  static final String SECRETS_MOUNT_PATH = "/weblogic-operator/secrets";
  static final String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";
  static final String STORAGE_MOUNT_PATH = "/shared";
  static final String NODEMGR_HOME = "/u01/nodemanager";
  static final String LOG_HOME = "/shared/logs";
  static final int FAILURE_THRESHOLD = 1;

  static final String READ_WRITE_MANY_ACCESS = "ReadWriteMany";

  @SuppressWarnings("OctalInteger")
  static final int ALL_READ_AND_EXECUTE = 0555;
}