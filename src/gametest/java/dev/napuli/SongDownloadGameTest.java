package dev.napuli;

import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SongDownloadGameTest {
    @GameTest public void duplicateAudioDownloadsAdvanceWithoutChangingExtensionCase(GameTestHelper h) throws Exception {
        var root=Files.createTempDirectory("napuli-song-download-test-");
        var executor=Executors.newSingleThreadExecutor(task->{
            var thread=new Thread(task,"napuli-song-download-test");thread.setDaemon(true);return thread;
        });
        try {
            for(String name:new String[]{"CANZONE.MP3","Canzone.OgG","Canzone.WAV","live.session.mp3"}) {
                var directory=Files.createTempDirectory(root,"format-");
                var original=directory.resolve(name);
                h.assertTrue(SongDownloadFiles.available(directory.toFile(),name).toPath().equals(original),
                        "A fresh download preserves its requested filename");
                Files.writeString(original,"original audio");
                int dot=name.lastIndexOf('.');
                String base=name.substring(0,dot),extension=name.substring(dot);
                for(int number=2;number<=4;number++) {
                    // A filename regression must fail promptly instead of hanging the server thread.
                    var candidate=executor.submit(()->SongDownloadFiles.available(directory.toFile(),name)).get(5,TimeUnit.SECONDS);
                    h.assertTrue(candidate.toPath().equals(directory.resolve(base+" ("+number+")"+extension)),
                            "Each duplicate advances past existing files and preserves the exact extension");
                    Files.writeString(candidate.toPath(),"duplicate audio");
                }
                h.assertTrue(Files.readString(original).equals("original audio"),"The original download is never overwritten");
            }
        } finally {
            executor.shutdownNow();
            try(var files=Files.walk(root)) {
                for(var path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
        h.succeed();
    }
}
