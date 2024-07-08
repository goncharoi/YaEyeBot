import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.VerRsrc.VS_FIXEDFILEINFO;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

//https://stackoverflow.com/questions/6918022/get-version-info-for-exe
public class EXEFileInfo {
    public static String getVersionInfo(String path) {
        IntByReference dwDummy = new IntByReference();
        dwDummy.setValue(0);

        int versionlength = com.sun.jna.platform.win32.Version.INSTANCE.GetFileVersionInfoSize(path, dwDummy);
        if (versionlength <= 0)
            return "";

        byte[] bufferarray = new byte[versionlength];

        Pointer lpData = new Memory(bufferarray.length);
        PointerByReference lplpBuffer = new PointerByReference();
        IntByReference puLen = new IntByReference();
        boolean fileInfoResult = com.sun.jna.platform.win32.Version.INSTANCE.GetFileVersionInfo(path, 0, versionlength, lpData);
        boolean verQueryVal = com.sun.jna.platform.win32.Version.INSTANCE.VerQueryValue(lpData, "\\", lplpBuffer, puLen);

        VS_FIXEDFILEINFO lplpBufStructure = new VS_FIXEDFILEINFO(lplpBuffer.getValue());
        lplpBufStructure.read();

        int v1 = (lplpBufStructure.dwFileVersionMS).intValue() >> 16;
        int v2 = (lplpBufStructure.dwFileVersionMS).intValue() & 0xffff;
        int v3 = (lplpBufStructure.dwFileVersionLS).intValue() >> 16;
        int v4 = (lplpBufStructure.dwFileVersionLS).intValue() & 0xffff;

        if (v1 != 0 || v2 != 0 || v3 != 0|| v4 != 0)
            return v1 + "." + v2 + "." + v3 + "." + v4;
        else
            return "";
    }
}