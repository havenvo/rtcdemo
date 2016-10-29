package haven.demo.webrtc.rtcdemo.model;

public class User {

    private int userId;
    private String userName;
    private String password;
    private String registrationId;

    public User(int userId, String userName, String password, String registrationId) {
        this.userId = userId;
        this.userName = userName;
        this.password = password;
        this.registrationId = registrationId;
    }

    public int getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getRegistrationId() {
        return registrationId;
    }
}
