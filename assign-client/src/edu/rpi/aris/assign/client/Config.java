package edu.rpi.aris.assign.client;

import java.io.File;
import java.util.prefs.Preferences;

public class Config {

    public static final ConfigProp<Boolean> ALLOW_INSECURE = new ConfigProp<>(null, null, false);
    public static final ConfigProp<File> ADD_CERT = new ConfigProp<>(null, null, null);

    public static final File CLIENT_CONFIG_DIR = new File(System.getProperty("user.home"), ".aris-java");
    private static final String SERVER_ADDRESS_KEY = "server_address";
    private static final String PORT_KEY = "port";
    private static final String USERNAME_KEY = "username";
    private static final String SELECTED_COURSE_ID_KEY = "selected_course";
    private static final String ACCESS_TOKEN_KEY = "access_token";
    private static final Preferences preferences = Preferences.userNodeForPackage(Config.class);

    public static final ConfigProp<String> SERVER_ADDRESS = new ConfigProp<>(preferences, SERVER_ADDRESS_KEY, preferences.get(SERVER_ADDRESS_KEY, null));
    public static final ConfigProp<Integer> PORT = new ConfigProp<>(preferences, PORT_KEY, preferences.getInt(PORT_KEY, 9001));
    public static final ConfigProp<String> USERNAME = new ConfigProp<>(preferences, USERNAME_KEY, preferences.get(USERNAME_KEY, null));
    public static final ConfigProp<Integer> SELECTED_COURSE_ID = new ConfigProp<>(preferences, SELECTED_COURSE_ID_KEY, preferences.getInt(SELECTED_COURSE_ID_KEY, 0));
    public static final ConfigProp<String> ACCESS_TOKEN = new ConfigProp<>(preferences, ACCESS_TOKEN_KEY, preferences.get(ACCESS_TOKEN_KEY, null));

}
