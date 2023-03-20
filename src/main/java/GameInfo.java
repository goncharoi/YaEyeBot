public class GameInfo implements Comparable<GameInfo> {
    public GameInfo(String gName, String gPath,String sPath, String gVers) {
        this.gName = gName;
        this.gPath = gPath;
        this.sPath = sPath;
        this.gVers = gVers;
    }

    public String gName;
    public String gPath;
    public String sPath;
    public String gVers;

    @Override
    public int compareTo(GameInfo o) {
        return o.gName.compareTo(gName);
    }
}