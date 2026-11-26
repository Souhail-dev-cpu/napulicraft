package dev.napuli.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.napuli.Napuli;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Bounded thumbnails of actual phone screenshots, decoded off the render thread. */
final class PhoneGallery {
    record Photo(String name,Identifier texture,int width,int height) {}
    private final List<Photo> photos=new ArrayList<>();
    private volatile int generation;
    private boolean requested,loading,failed;
    List<Photo> photos(){return List.copyOf(photos);}
    boolean loading(){return loading;}
    boolean failed(){return failed;}
    void load(Minecraft mc){
        if(requested)return;requested=true;loading=true;failed=false;int epoch=++generation;
        Thread.ofVirtual().name("Napuli phone gallery").start(()->{
            try{
                Path directory=mc.gameDirectory.toPath().resolve("screenshots");List<Path> files;
                if(!Files.isDirectory(directory))files=List.of();
                else try(var stream=Files.list(directory)){files=stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().matches("telefono-[0-9]+\\.png")).sorted(Comparator.comparingLong(PhoneGallery::modified).reversed()).limit(24).toList();}
                for(Path file:files){
                    if(epoch!=generation)break;
                    try{
                        if(Files.size(file)>32L*1024*1024)continue;
                        java.awt.image.BufferedImage thumb;
                        try(var source=ImageIO.createImageInputStream(file.toFile())){
                            var readers=ImageIO.getImageReaders(source);if(!readers.hasNext())continue;var reader=readers.next();
                            try{reader.setInput(source);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>16384||h>16384)continue;var params=reader.getDefaultReadParam();int sample=Math.max(1,Math.max(w,h)/640);params.setSourceSubsampling(sample,sample,0,0);thumb=reader.read(0,params);}finally{reader.dispose();}
                        }
                        var bytes=new ByteArrayOutputStream();ImageIO.write(thumb,"PNG",bytes);var image=NativeImage.read(bytes.toByteArray());int w=thumb.getWidth(),h=thumb.getHeight();thumb.flush();
                        mc.execute(()->{if(epoch!=generation){image.close();return;}var id=Napuli.id("phone/gallery/"+UUID.randomUUID());mc.getTextureManager().register(id,new DynamicTexture(()->"Napuli phone photo",image));photos.add(new Photo(file.getFileName().toString(),id,w,h));});
                    }catch(Exception unreadable){mc.execute(()->{if(epoch==generation)failed=true;});}
                }
            }catch(Exception failure){mc.execute(()->{if(epoch==generation)failed=true;});}
            finally{mc.execute(()->{if(epoch==generation)loading=false;});}
        });
    }
    void close(Minecraft mc){generation++;for(var photo:photos)mc.getTextureManager().release(photo.texture);photos.clear();requested=false;loading=false;}
    private static long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(Exception ignored){return 0;}}
}
