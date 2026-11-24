package dev.napuli;

import java.io.File;

/** Chooses an unused download name, preserving the original extension and its case. */
public final class SongDownloadFiles {
    private SongDownloadFiles() {}

    public static File available(File directory,String name) {
        int dot=name.lastIndexOf('.');
        String base=dot>0?name.substring(0,dot):name;
        String extension=dot>0?name.substring(dot):"";
        File target=new File(directory,name);
        for(int index=2;target.exists();index++)
            target=new File(directory,base+" ("+index+")"+extension);
        return target;
    }
}
