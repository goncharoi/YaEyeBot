public class GameInfo implements Comparable<GameInfo> {
    public GameInfo(String gName, String gPath, String sPath, String gVers, String sPathSteam) {
        setGameInfo(gName, gPath, sPath, gVers);
        this.sPathSteam = sPathSteam;
    }

    public GameInfo(String gName, String gPath, String sPath, String gVers) {
        setGameInfo(gName, gPath, sPath, gVers);
        this.sPathSteam = "";
    }

    private void setGameInfo(String gName, String gPath, String sPath, String gVers) {
        this.gName = gName;
        this.gPath = gPath;
        this.sPath = sPath;
        this.gVers = gVers;
    }

    public String gName;
    public String gPath;
    public String sPath;
    public String gVers;
    public String sPathSteam;

    @Override
    public int compareTo(GameInfo o) {
        return o.gName.compareTo(gName);
    }
}