package dev.napuli.client;

import dev.napuli.PhonePacket;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** 16 kHz mono PCM, 40 ms frames. Devices are opened off the render thread; the microphone starts muted. */
final class PhoneAudio {
    private static final AudioFormat FORMAT=new AudioFormat(16000,16,1,true,false);
    private static final ArrayBlockingQueue<byte[]> QUEUE=new ArrayBlockingQueue<>(12);
    private static volatile UUID call;
    private static volatile boolean microphone;
    private static volatile TargetDataLine capture;
    private static volatile SourceDataLine speaker;
    private static volatile int captureEpoch, playbackEpoch;
    private static boolean startingSpeaker;
    static volatile String error="";
    static boolean muted(){return !microphone;}
    static void call(UUID next){if(java.util.Objects.equals(call,next))return;stop();call=next;error="";}
    static void toggle(){if(call==null)return;if(microphone){captureEpoch++;microphone=false;var line=capture;if(line!=null)line.close();return;}int epoch=++captureEpoch;microphone=true;error="";UUID session=call;
        Thread.ofVirtual().name("Napuli phone microphone").start(()->{
            TargetDataLine line=null;
            try{
                line=AudioSystem.getTargetDataLine(FORMAT);if(epoch!=captureEpoch)return;capture=line;line.open(FORMAT,PhonePacket.Voice.FRAME_BYTES*4);line.start();
                while(epoch==captureEpoch&&microphone&&session.equals(call)){
                    byte[] data=new byte[PhonePacket.Voice.FRAME_BYTES];int offset=0;
                    while(offset<data.length&&epoch==captureEpoch&&microphone&&session.equals(call)){int n=line.read(data,offset,data.length-offset);if(n<1)break;offset+=n;}
                    if(offset!=data.length)continue;
                    Minecraft.getInstance().execute(()->{if(epoch==captureEpoch&&microphone&&session.equals(call)&&ClientPlayNetworking.canSend(PhonePacket.Voice.TYPE))ClientPlayNetworking.send(new PhonePacket.Voice(session,data));});
                }
            }catch(Exception failure){if(epoch==captureEpoch&&microphone&&session.equals(call))error="phone.mic_error";}
            finally{if(line!=null)line.close();if(epoch==captureEpoch&&session.equals(call))microphone=false;if(capture==line)capture=null;}
        });
    }
    static void receive(PhonePacket.Voice frame){
        if(!frame.call().equals(call))return;
        if(!QUEUE.offer(frame.pcm().clone())){QUEUE.poll();QUEUE.offer(frame.pcm().clone());}
        if(speaker!=null)return;
        synchronized(QUEUE){if(speaker!=null||startingSpeaker)return;
            startingSpeaker=true;UUID session=call;int epoch=playbackEpoch;
            Thread.ofVirtual().name("Napuli phone speaker").start(()->{
                SourceDataLine line=null;
                try{line=AudioSystem.getSourceDataLine(FORMAT);if(epoch!=playbackEpoch)return;speaker=line;line.open(FORMAT,PhonePacket.Voice.FRAME_BYTES*4);line.start();
                    while(epoch==playbackEpoch&&session.equals(call)){byte[] data=QUEUE.poll(250,TimeUnit.MILLISECONDS);if(data!=null&&session.equals(call))line.write(data,0,data.length);}
                }catch(Exception failure){if(epoch==playbackEpoch&&session.equals(call))error="phone.speaker_error";}
                finally{if(line!=null)line.close();synchronized(QUEUE){if(epoch==playbackEpoch){speaker=null;startingSpeaker=false;QUEUE.clear();}}}
            });
        }
    }
    static void stop(){call=null;captureEpoch++;playbackEpoch++;microphone=false;QUEUE.clear();var mic=capture;if(mic!=null)mic.close();capture=null;var out=speaker;if(out!=null)out.close();speaker=null;synchronized(QUEUE){startingSpeaker=false;}}
    private PhoneAudio(){}
}
