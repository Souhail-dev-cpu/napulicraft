package dev.napuli.client;

import static dev.napuli.CityText.t;
import com.mojang.blaze3d.platform.InputConstants;
import dev.napuli.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Interactive handset, with ColorOS flow cards and OriginOS modular widgets. */
final class PhoneScreen extends Screen {
    private PhonePacket state;
    private String app="home",query="",draft="",numberDraft="",dial="",noteDraft="",calculator="",answer="";
    private String pendingNumber="",pendingSend;
    private final Map<UUID,String> drafts=new HashMap<>();
    private final PhoneGallery gallery=new PhoneGallery();
    private UUID peer,note;
    private int page,ticks,x,y,toastTicks,sendTicks,noteTicks,photoIndex,lastGallerySize=-1;
    private float scale;
    private EditBox input;
    private Button sendButton;
    private boolean filterDirty,micWasMuted=true,notePending;
    private Component toast=Component.empty();
    private static final int W=248,H=512;

    PhoneScreen(PhonePacket state){super(Component.literal(PhoneModel.values()[state.model()].name));this.state=state;if(state.call()!=null)app="calls";}
    PhonePacket snapshot(){return state;}
    String app(){return app;}
    private PhoneModel model(){return PhoneModel.values()[state.model()];}
    private boolean colorOS(){return model().colorOS();}
    private int homeInk(){return model()==PhoneModel.VIVO_X500_PRO_MAX?0xff2b241e:0xffffffff;}
    private int ink(){return PhoneClient.dark?0xfff3f5fa:0xff182337;}
    private int muted(){return PhoneClient.dark?0xffabb8cc:0xff637086;}
    private int surface(){return PhoneClient.dark?0xff242b3a:0xfffafbff;}
    private int accent(){return colorOS()?0xff278a65:0xff5078ed;}
    private int background(){return PhoneClient.dark?0xff121723:colorOS()?0xfff0f3f2:0xffeef0f8;}
    private int px(float v){return x+Math.round(v*scale);}
    private int py(float v){return y+Math.round(v*scale);}
    private long unread(){return state.messages().stream().filter(m->!m.outgoing()&&!m.read()).count();}
    private String name(UUID id){return state.contacts().stream().filter(c->c.id().equals(id)).map(PhonePacket.Contact::name).findFirst().orElseGet(()->state.messages().stream().filter(m->m.peer().equals(id)).map(PhonePacket.Text::name).findFirst().orElse("?"));}
    private PhonePacket.Contact selected(){return state.contacts().stream().filter(c->c.id().equals(peer)).findFirst().orElseGet(()->state.personal().history().stream().filter(c->c.peer().equals(peer)).map(c->new PhonePacket.Contact(c.peer(),c.name(),c.number(),false,false)).findFirst().orElse(null));}
    void update(PhonePacket next){
        boolean changed=state.model()!=next.model()||state.airplane()!=next.airplane()||!state.goal().equals(next.goal())
            ||!state.number().equals(next.number())||!state.contacts().equals(next.contacts())||!state.messages().equals(next.messages())
            ||!state.personal().equals(next.personal())||(state.call()==null)!=(next.call()==null)
            ||state.call()!=null&&next.call()!=null&&(!state.call().id().equals(next.call().id())||state.call().active()!=next.call().active());
        boolean incoming=next.call()!=null&&next.call().incoming()&&(state.call()==null||!state.call().id().equals(next.call().id()));
        state=next;
        if(!next.status().getString().isBlank()){
            notice(next.status());
            if(notePending&&next.status().equals(t("phone.note_saved"))){notePending=false;if(app.equals("note_edit")){go("notes");return;}}
            if(notePending&&(next.status().equals(t("phone.notes_full"))||next.status().equals(t("phone.invalid_text"))))notePending=false;
            if(pendingSend!=null&&next.status().equals(t("phone.message_sent"))){pendingSend=null;draft="";drafts.put(peer,"");if(input!=null&&app.equals("thread"))input.setValue("");}
            else if(pendingSend!=null&&(next.status().equals(t("phone.unavailable"))||next.status().equals(t("phone.invalid_text"))||next.status().equals(t("phone.unknown_number"))))pendingSend=null;
        }
        if(!pendingNumber.isEmpty()){
            var contact=next.contacts().stream().filter(c->c.number().equals(pendingNumber)).findFirst().orElse(null);
            if(contact!=null){pendingNumber="";chat(contact.id());return;}
        }
        if(incoming){go("calls");return;}
        if(app.equals("thread")&&next.messages().stream().anyMatch(m->m.peer().equals(peer)&&!m.outgoing()&&!m.read()))request("read",peer.toString(),"");
        if(changed)refreshWidgets();
    }
    private void notice(Component text){toast=text;toastTicks=120;}
    void photoSaved(boolean success){notice(t(success?"phone.photo_saved":"phone.photos_error"));gallery.close(minecraft);}
    @Override protected void init(){saveInput();scale=Math.max(.1f,Math.min((width-16f)/W,(height-12f)/H));x=Math.round((width-W*scale)/2);y=Math.round((height-H*scale)/2);build();}
    private void saveInput(){
        if(input==null)return;
        switch(app){case "thread"->{draft=input.getValue();if(peer!=null)drafts.put(peer,draft);}case "note_edit"->noteDraft=input.getValue();case "calls"->dial=input.getValue();case "add_contact","compose"->numberDraft=input.getValue();default->query=input.getValue();}
    }
    private void refreshWidgets(){boolean focus=input!=null&&input.isFocused();int cursor=input==null?0:input.getCursorPosition();saveInput();rebuildWidgets();if(focus&&input!=null){input.setCursorPosition(Math.min(cursor,input.getValue().length()));setFocused(input);}}
    private void go(String next){saveInput();app=next;query="";page=0;if(next.equals("photos"))gallery.load(minecraft);rebuildWidgets();}
    private void chat(UUID id){saveInput();peer=id;draft=drafts.getOrDefault(id,"");pendingSend=null;app="thread";query="";page=0;request("read",id.toString(),"");rebuildWidgets();}
    private void detail(UUID id){saveInput();peer=id;app="contact";query="";page=0;rebuildWidgets();}
    private void back(){go(switch(app){case "thread","compose","confirm_delete"->"messages";case "contact","add_contact","mycard"->"contacts";case "offers"->"share";case "recents","services"->"calls";case "taxi"->"services";case "note_edit"->"notes";case "photo"->"photos";case "battery"->"settings";default->"home";});}
    private void request(String op,String target,String body){PhoneClient.request(op,target,body);}
    private void sendMessage(){
        if(peer==null||input==null||input.getValue().isBlank()||pendingSend!=null)return;
        pendingSend=input.getValue();sendTicks=0;request("message",peer.toString(),pendingSend);refreshWidgets();
    }
    @Override public void tick(){
        super.tick();if(toastTicks>0)toastTicks--;
        if(filterDirty){filterDirty=false;refreshWidgets();}
        if(pendingSend!=null&&++sendTicks>160){pendingSend=null;notice(t("phone.retry"));refreshWidgets();}
        if(notePending&&++noteTicks>160){notePending=false;notice(t("phone.retry"));}
        if(micWasMuted!=PhoneAudio.muted()){micWasMuted=PhoneAudio.muted();if(app.equals("calls"))refreshWidgets();}
        if(app.equals("photos")&&lastGallerySize!=gallery.photos().size()){lastGallerySize=gallery.photos().size();refreshWidgets();}
        if(++ticks%40==0)request("refresh","","");
    }
    @Override public void removed(){saveInput();gallery.close(minecraft);super.removed();request("close","","");}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){}
    private void build(){
        input=null;sendButton=null;
        tap(89,486,70,16,t("phone.home"),()->go("home"),0,false,g->{});
        if(!app.equals("home"))tap(17,43,26,25,Component.literal("‹"),this::back,0,false);
        switch(app){
            case "home"->{
                String[] icons={"gps","contacts","photos","notes","calculator","settings","about","share"};
                for(int i=0;i<icons.length;i++){String id=icons[i];icon(27+i%4*51,268+i/4*68,id,()->go(id));}
                icon(39,422,"calls",()->go("calls"));icon(103,422,"messages",()->go("messages"));icon(167,422,"camera",()->go("camera"));
                tap(208,35,21,22,t("phone.notifications"),()->go("notifications"),0,false);
                tap(22,169,204,73,t("phone.mycard"),()->go("mycard"),0,false);
                if(state.call()!=null)tap(67,37,126,19,t("phone.active_call",state.call().name()),()->go("calls"),0,false);
            }
            case "contacts"->{
                tap(24,82,200,43,t("phone.mycard"),()->go("mycard"),surface(),false,g->{text(g,t("phone.mycard").getString(),34,90,muted(),180);text(g,PhoneNumbers.format(state.number()),34,107,ink(),180);});
                tap(201,43,25,25,Component.literal("+"),()->go("add_contact"),0,false);
                edit(24,137,200,27,query,64,t("phone.search"),v->{query=v;page=0;filterDirty=true;});
                var contacts=filteredContacts(true);int start=pageStart(contacts.size(),4);
                for(int i=start;i<Math.min(start+4,contacts.size());i++){var c=contacts.get(i);contact(24,176+(i-start)*57,c,()->detail(c.id()),"");}
                paging(contacts.size(),4,438);
                tap(69,438,110,24,t("phone.exchange"),()->go("share"),surface(),false);
            }
            case "messages"->{
                tap(201,43,25,25,Component.literal("+"),()->go("compose"),0,false);
                edit(24,84,200,27,query,64,t("phone.search"),v->{query=v;page=0;filterDirty=true;});
                var peers=conversations();int start=pageStart(peers.size(),4);
                for(int i=start;i<Math.min(start+4,peers.size());i++){UUID id=peers.get(i);var last=state.messages().stream().filter(m->m.peer().equals(id)).reduce((a,b)->b).orElseThrow();
                    var c=state.contacts().stream().filter(e->e.id().equals(id)).findFirst().orElse(new PhonePacket.Contact(id,last.name(),"",false,false));
                    contact(24,124+(i-start)*72,c,()->chat(id),last.body());
                }
                paging(peers.size(),4,438);
            }
            case "compose","add_contact"->{
                edit(24,133,200,35,numberDraft,32,t("phone.number_hint"),v->numberDraft=v);
                tap(24,187,200,36,t(app.equals("compose")?"phone.save_write":"phone.save_contact"),()->{
                    String value=PhoneNumbers.normalize(input.getValue());if(value.isEmpty()){notice(t("phone.invalid_number"));return;}
                    if(app.equals("compose"))pendingNumber=value;request("add_contact",value,"");
                },accent(),true);
                tap(24,241,200,32,t("phone.exchange"),()->go("share"),surface(),false);
            }
            case "mycard"->{
                tap(24,236,200,37,t("phone.copy_number"),()->{minecraft.keyboardHandler.setClipboard(PhoneNumbers.format(state.number()));notice(t("phone.copied"));},accent(),true);
                tap(24,289,200,37,t("phone.exchange"),()->go("share"),surface(),false);
            }
            case "share","offers"->{
                tabs(new String[]{"share","offers"},new String[]{"phone.nearby","phone.requests"});
                if(app.equals("share")){
                    var nearby=state.personal().nearby();int start=pageStart(nearby.size(),4);
                    for(int i=start;i<Math.min(start+4,nearby.size());i++){var c=nearby.get(i);contact(24,139+(i-start)*66,c,()->request("share",c.id().toString(),""),t("phone.exchange").getString());}
                    paging(nearby.size(),4,438);
                }else{
                    var offers=state.personal().offers();int start=pageStart(offers.size(),3);
                    for(int i=start;i<Math.min(start+3,offers.size());i++){var o=offers.get(i);int by=125+(i-start)*99;
                        tap(24,by,200,43,Component.literal(o.name()),()->{},surface(),false,g->{text(g,o.name()+" · "+o.seconds()+"s",33,by+8,ink(),183);text(g,PhoneNumbers.format(o.number()),33,by+26,muted(),183);});
                        tap(24,by+47,97,28,t("phone.accept"),()->request("accept_contact",o.token().toString(),""),accent(),true);
                        tap(127,by+47,97,28,t("phone.decline"),()->request("decline_contact",o.token().toString(),""),surface(),false);
                    }paging(offers.size(),3,438);
                }
            }
            case "contact"->{
                var c=selected();if(c!=null){
                    tap(24,239,97,37,t("phone.call"),()->{request("call",c.id().toString(),"");go("calls");},accent(),true);
                    tap(127,239,97,37,t("phone.messages"),()->chat(c.id()),surface(),false);
                    tap(24,295,200,35,t(c.saved()?"phone.remove_contact":"phone.save_contact"),()->request(c.saved()?"remove_contact":"add_contact",c.saved()?c.id().toString():c.number(),""),surface(),false);
                    tap(24,346,200,35,t("phone.copy_number"),()->{minecraft.keyboardHandler.setClipboard(PhoneNumbers.format(c.number()));notice(t("phone.copied"));},surface(),false);
                }
            }
            case "calls","recents"->{
                if(state.call()!=null){var c=state.call();
                    if(c.incoming()&&!c.active())tap(48,294,152,41,t("phone.answer"),()->request("answer",c.id().toString(),""),0xff28a86c,true);
                    if(c.active()){
                        tap(25,285,96,48,t(PhoneAudio.muted()?"phone.mic_enable":"phone.mic_disable"),()->{PhoneAudio.toggle();rebuildWidgets();},surface(),false);
                        tap(127,285,96,48,t(PhoneAudio.speakerMuted()?"phone.audio_off":"phone.audio_on"),()->{PhoneAudio.toggleSpeaker();rebuildWidgets();},surface(),false);
                    }
                    tap(49,365,150,41,t("phone.end"),()->request("end",c.id().toString(),""),0xffd84b60,true);
                    tap(39,421,170,27,t("phone.call_chat"),()->chat(c.peer()),0,false);
                }else{
                    tabs(new String[]{"calls","recents","contacts"},new String[]{"phone.dialpad","phone.recents","phone.contacts"});
                    if(app.equals("calls")){
                        edit(24,126,200,35,dial,32,t("phone.number_hint"),v->dial=v);
                        String[] keys={"1","2","3","4","5","6","7","8","9","+","0","⌫"};
                        for(int i=0;i<keys.length;i++){String key=keys[i];int bx=37+i%3*61,by=190+i/3*47;
                            tap(bx,by,51,40,Component.literal(key),()->{if(key.equals("⌫"))dial=dial.isEmpty()?"":dial.substring(0,dial.length()-1);else if(dial.length()<32)dial+=key;input.setValue(dial);},surface(),false,g->large(g,key,bx+25,by+11,ink(),1.7f));
                        }
                        tap(70,399,108,39,t("phone.call"),()->{if(!dial.isBlank()){request("call",dial,"");if(PhoneNumbers.normalize(dial).equals(PhoneNumbers.normalize(PhoneServices.TAXI_NUMBER)))go("taxi");}},0xff2caa6e,true);
                        tap(24,443,200,24,t("phone.services"),()->go("services"),surface(),false);
                    }else{
                        var history=new ArrayList<>(state.personal().history());Collections.reverse(history);int start=pageStart(history.size(),5);
                        for(int i=start;i<Math.min(start+5,history.size());i++){var h=history.get(i);contact(24,125+(i-start)*59,new PhonePacket.Contact(h.peer(),h.name(),h.number(),false,false),()->detail(h.peer()),(h.incoming()?"↙ ":"↗ ")+t("phone.call_"+h.outcome()).getString()+" · "+time(h.time()));}
                        paging(history.size(),5,438);
                    }
                }
            }
            case "services"->{
                tap(24,112,200,50,t("phone.service.pizza_button"),()->request("call",PhoneServices.PIZZA_NUMBER,""),accent(),true);
                tap(24,183,200,50,t("phone.service.police_button"),()->request("call","112",""),surface(),false);
                tap(24,254,200,50,t("phone.service.taxi_button"),()->go("taxi"),surface(),false);
                tap(24,351,200,34,t("phone.service.cancel_pizza"),()->request("cancel_service","pizza",""),surface(),false);
                tap(24,400,200,34,t("phone.service.cancel_taxi"),()->request("cancel_service","taxi",""),surface(),false);
            }
            case "taxi"->{
                edit(24,84,200,27,query,64,t("phone.search"),v->{query=v;page=0;filterDirty=true;});
                var places=filteredPlaces().stream().filter(p->p.position().getY()>=60).toList();int start=pageStart(places.size(),5);
                for(int i=start;i<Math.min(start+5,places.size());i++){var place=places.get(i);
                    tap(24,129+(i-start)*54,200,43,place.name(),()->request("taxi",place.id(),""),surface(),false);}
                paging(places.size(),5,438);
            }
            case "thread"->{
                tap(47,43,151,28,Component.literal(name(peer)),()->detail(peer),0,false);
                tap(202,43,25,25,Component.literal("⋮"),()->go("confirm_delete"),0,false);
                edit(24,385,200,31,draft,256,t("phone.write"),v->{draft=v;drafts.put(peer,v);});
                sendButton=tap(170,426,54,32,t(pendingSend==null?"phone.send":"phone.sending"),this::sendMessage,accent(),true);sendButton.active=pendingSend==null;
                tap(24,426,69,32,t("phone.position"),()->request("share_position",peer.toString(),""),surface(),false);
                tap(99,426,65,32,t("phone.call"),()->{request("call",peer.toString(),"");go("calls");},surface(),false);
                paging(messagePages().size(),1,351);
            }
            case "confirm_delete"->{
                tap(24,202,200,37,t("phone.delete_thread"),()->{request("delete_thread",peer.toString(),"");go("messages");},0xffcf5160,true);
                tap(24,257,200,35,t("phone.cancel"),()->chat(peer),surface(),false);
            }
            case "gps"->{
                edit(24,79,200,27,query,64,t("phone.search"),v->{query=v;page=0;filterDirty=true;});var places=filteredPlaces();int start=pageStart(places.size(),3);
                for(int i=start;i<Math.min(start+3,places.size());i++){var p=places.get(i);tap(24,285+(i-start)*40,200,34,p.name(),()->request("navigate",p.id(),""),surface(),false);}
                paging(places.size(),3,411);
                tap(24,443,200,22,t(state.goal().isEmpty()?"phone.to_napoli":"phone.stop_gps"),()->{if(state.goal().isEmpty()){onClose();minecraft.getConnection().sendCommand("napoli");}else request("stop_gps","","");},0,false);
            }
            case "settings"->{
                String[] labels={"phone.mycard",state.airplane()?"phone.airplane_on":"phone.airplane_off",PhoneClient.dark?"phone.dark_on":"phone.dark_off",CompanionOptions.current.phoneSilent?"phone.silent_on":"phone.silent_off","phone.battery","phone.about"};
                for(int i=0;i<labels.length;i++){int option=i;tap(24,94+i*57,200,46,t(labels[i]),()->{switch(option){case 0->go("mycard");case 1->request("airplane","","");case 2->toggleDark();case 3->toggleSilent();case 4->go("battery");case 5->go("about");}},surface(),false);}
            }
            case "battery"->tap(24,313,200,40,t("phone.recharge"),()->request("charge","",""),accent(),true);
            case "about"->paging(12,6,434);
            case "camera"->{
                tap(98,415,52,46,t("phone.capture"),this::photo,0xffeeeeee,false);
                tap(26,425,60,30,t("phone.photos"),()->go("photos"),0,true);
            }
            case "photos"->{
                gallery.load(minecraft);var photos=gallery.photos();int start=pageStart(photos.size(),6);
                for(int i=start;i<Math.min(start+6,photos.size());i++){int index=i,bx=24+(i-start)%2*104,by=106+(i-start)/2*91;var p=photos.get(i);
                    tap(bx,by,96,81,Component.literal(p.name()),()->{photoIndex=index;go("photo");},surface(),false,g->drawPhoto(g,p,bx+3,by+3,90,75));
                }
                paging(photos.size(),6,392);
                tap(24,435,200,27,t("phone.photos_folder"),()->PhonePhoto.openFolder(minecraft),surface(),false);
            }
            case "photo"->tap(24,430,200,30,t("phone.photos_folder"),()->PhonePhoto.openFolder(minecraft),surface(),false);
            case "notes"->{
                tap(201,43,25,25,Component.literal("+"),()->{note=null;noteDraft="";go("note_edit");},0,false);
                var notes=new ArrayList<>(state.personal().notes());Collections.reverse(notes);int start=pageStart(notes.size(),5);
                for(int i=start;i<Math.min(start+5,notes.size());i++){var n=notes.get(i);int by=104+(i-start)*62;
                    tap(24,by,200,54,Component.literal(n.body()),()->{note=n.id();noteDraft=n.body();go("note_edit");},surface(),false,g->{paragraph(g,Component.literal(n.body()),34,by+10,179,3,ink());});
                }paging(notes.size(),5,438);
            }
            case "note_edit"->{
                edit(24,103,200,32,noteDraft,256,t("phone.note_hint"),v->noteDraft=v);
                tap(24,353,200,35,t("phone.save_note"),()->{if(!notePending){notePending=true;noteTicks=0;request("save_note",note==null?"":note.toString(),noteDraft);}},accent(),true);
                if(note!=null)tap(24,406,200,32,t("phone.delete_note"),()->{request("delete_note",note.toString(),"");go("notes");},surface(),false);
            }
            case "calculator"->{
                String[] keys={"C","⌫","%","÷","7","8","9","×","4","5","6","−","1","2","3","+","±","0",".","="};
                for(int i=0;i<keys.length;i++){String key=keys[i];int bx=24+i%4*51,by=211+i/4*47;
                    tap(bx,by,46,41,Component.literal(key),()->calculate(key),key.equals("=")?accent():surface(),key.equals("="),g->large(g,key,bx+23,by+13,key.equals("=")?0xffffffff:ink(),1.5f));
                }
            }
            case "notifications"->{
                tap(24,83,63,43,t("phone.airplane_short"),()->request("airplane","",""),state.airplane()?accent():surface(),state.airplane());
                tap(92,83,63,43,t("phone.theme"),this::toggleDark,surface(),false);
                tap(160,83,64,43,t("phone.ringtone"),this::toggleSilent,CompanionOptions.current.phoneSilent?surface():accent(),!CompanionOptions.current.phoneSilent);
                if(!state.personal().offers().isEmpty())tap(24,147,200,49,t("phone.pending_contacts",state.personal().offers().size()),()->go("offers"),surface(),false);
                var peers=conversations().stream().filter(id->state.messages().stream().anyMatch(m->m.peer().equals(id)&&!m.outgoing()&&!m.read())).limit(3).toList();
                for(int i=0;i<peers.size();i++){UUID id=peers.get(i);tap(24,218+i*67,200,54,Component.literal(name(id)),()->chat(id),surface(),false,g->{text(g,name(id),35,230+peers.indexOf(id)*67,ink(),174);text(g,t("phone.unread_messages").getString(),35,247+peers.indexOf(id)*67,muted(),174);});}
            }
            default->{}
        }
    }
    private void edit(int bx,int by,int bw,int bh,String value,int max,Component hint,java.util.function.Consumer<String> listener){
        input=new EditBox(font,px(bx),py(by),Math.round(bw*scale),Math.max(12,Math.round(bh*scale)),hint);input.setMaxLength(max);input.setValue(value);input.setHint(hint);input.setResponder(listener);addRenderableWidget(input);
    }
    private void tabs(String[] targets,String[] labels){int w=200/targets.length;for(int i=0;i<targets.length;i++){String target=targets[i];tap(24+i*w,82,w-3,25,t(labels[i]),()->go(target),app.equals(target)?accent():surface(),app.equals(target));}}
    private void toggleDark(){PhoneClient.dark=!PhoneClient.dark;CompanionOptions.current.phoneDark=PhoneClient.dark;CompanionOptions.current.save();refreshWidgets();}
    private void toggleSilent(){CompanionOptions.current.phoneSilent=!CompanionOptions.current.phoneSilent;CompanionOptions.current.save();notice(t(CompanionOptions.current.phoneSilent?"phone.silent_on":"phone.silent_off"));refreshWidgets();}
    private void icon(int bx,int by,String id,Runnable action){
        tap(bx-3,by-3,45,59,t("phone."+id),action,0,false,g->{
            rounded(g,bx+1,by+3,37,37,12,0x35000000);g.blit(Napuli.id("textures/phone/icons/"+id+".png"),bx,by,bx+38,by+38,0,1,0,1);
            center(g,t("phone."+id).getString(),bx+19,by+44,0xffffffff,49);
            long badge=id.equals("messages")?unread():id.equals("contacts")||id.equals("share")?state.personal().offers().size():0;
            if(badge>0){rounded(g,bx+29,by-4,16,15,7,0xffed525c);center(g,Long.toString(Math.min(99,badge)),bx+37,by,0xffffffff,15);}
        });
    }
    private void contact(int bx,int by,PhonePacket.Contact c,Runnable action,String preview){
        tap(bx,by,200,preview.isEmpty()?51:60,Component.literal(c.name()),action,surface(),false,g->{
            avatar(g,c.name(),bx+9,by+10,30);text(g,c.name(),bx+48,by+11,ink(),143);
            String detail=preview.isEmpty()?PhoneNumbers.format(c.number()):preview;text(g,detail,bx+48,by+30,muted(),143);
            if(c.online())rounded(g,bx+31,by+32,7,7,3,0xff41bd80);
            if(state.messages().stream().anyMatch(m->m.peer().equals(c.id())&&!m.outgoing()&&!m.read()))rounded(g,bx+183,by+11,7,7,3,accent());
        });
    }
    private Button tap(int bx,int by,int bw,int bh,Component label,Runnable action,int color,boolean white){return tap(bx,by,bw,bh,label,action,color,white,null);}
    private Button tap(int bx,int by,int bw,int bh,Component label,Runnable action,int color,boolean white,java.util.function.Consumer<GuiGraphicsExtractor> contents){
        var button=new Button(px(bx),py(by),Math.max(1,Math.round(bw*scale)),Math.max(1,Math.round(bh*scale)),label,b->action.run(),s->s.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt){
                g.pose().pushMatrix();g.pose().translate(PhoneScreen.this.x,PhoneScreen.this.y);g.pose().scale(scale);
                if(color!=0){rounded(g,bx,by+1,bw,bh,Math.min(colorOS()?13:17,bh/2),0x10000000);rounded(g,bx,by,bw,bh,Math.min(colorOS()?13:17,bh/2),color);}
                if(isHoveredOrFocused())rounded(g,bx,by,bw,bh,Math.min(12,bh/2),0x254b88c9);
                if(contents!=null)contents.accept(g);else center(g,label.getString(),bx+bw/2,by+(bh-8)/2,white?0xffffffff:ink(),bw-12);
                g.pose().popMatrix();
            }
        };button.setTooltip(Tooltip.create(label));return addRenderableWidget(button);
    }
    private List<PhonePacket.Contact> filteredContacts(boolean saved){String q=CompanionBoard.normalize(query);String digits=query.replaceAll("[^0-9]","");return state.contacts().stream().filter(c->!saved||c.saved()).filter(c->CompanionBoard.normalize(c.name()+" "+PhoneNumbers.format(c.number())).contains(q)||!digits.isEmpty()&&c.number().contains(digits)).toList();}
    private List<UUID> conversations(){
        var ids=new LinkedHashSet<UUID>();var all=new ArrayList<>(state.messages());Collections.reverse(all);
        for(var m:all)if(CompanionBoard.normalize(m.name()+" "+m.body()).contains(CompanionBoard.normalize(query)))ids.add(m.peer());return List.copyOf(ids);
    }
    private List<PhonePacket.Place> filteredPlaces(){return state.places().stream().filter(p->CompanionBoard.normalize(p.name().getString()).contains(CompanionBoard.normalize(query))).toList();}
    private List<List<PhonePacket.Text>> messagePages(){
        var all=state.messages().stream().filter(m->m.peer().equals(peer)).toList();var pages=new ArrayList<List<PhonePacket.Text>>();var entries=new ArrayList<PhonePacket.Text>();int used=0;
        for(int i=all.size()-1;i>=0;i--){var m=all.get(i);int height=messageHeight(m);if(used+height>238&&!entries.isEmpty()){pages.add(List.copyOf(entries));entries.clear();used=0;}entries.addFirst(m);used+=height;}
        if(!entries.isEmpty())pages.add(List.copyOf(entries));if(pages.isEmpty())pages.add(List.of());return pages;
    }
    private int messageHeight(PhonePacket.Text m){return font.split(Component.literal(m.body()),158).size()*10+25;}
    private int pageStart(int count,int size){page=Math.clamp(page,0,Math.max(0,(count-1)/size));return page*size;}
    private void paging(int count,int size,int by){int pages=Math.max(1,(count+size-1)/size);page=Math.clamp(page,0,pages-1);if(page>0)tap(24,by,39,24,Component.literal("‹"),()->{saveInput();page--;rebuildWidgets();},surface(),false);if(page+1<pages)tap(185,by,39,24,Component.literal("›"),()->{saveInput();page++;rebuildWidgets();},surface(),false);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(mx<px(13)||mx>px(235)||my<py(72)||my>py(470)||vertical==0||Set.of("home","settings","camera","calls","calculator","note_edit","contact","mycard","battery","photo").contains(app))return super.mouseScrolled(mx,my,horizontal,vertical);
        saveInput();page=Math.max(0,page+(vertical<0?1:-1));rebuildWidgets();return true;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(event.key()==InputConstants.KEY_RETURN&&app.equals("thread")){sendMessage();return true;}
        if(event.key()==InputConstants.KEY_ESCAPE&&!app.equals("home")){back();return true;}
        if(app.equals("calculator")){if(event.key()==InputConstants.KEY_RETURN){calculate("=");return true;}if(event.key()==InputConstants.KEY_BACKSPACE){calculate("⌫");return true;}}
        return super.keyPressed(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        if(!app.equals("camera"))g.fill(0,0,width,height,0xa0080d18);
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale);
        g.blit(Napuli.id("textures/phone/"+model().id+(app.equals("camera")?"_viewfinder":"_front")+".png"),0,0,W,H,0,1,0,1);
        boolean wallpaper=app.equals("home")||app.equals("camera");
        if(!wallpaper)rounded(g,13,33,222,445,23,background());
        int statusColor=app.equals("home")?homeInk():app.equals("camera")?0xffffffff:ink();
        text(g,clock(),29,18,statusColor,48);text(g,state.airplane()?"✈":"5G",171,18,statusColor,24);
        rounded(g,201,17,21,9,3,statusColor);rounded(g,202,18,Math.max(1,state.charge()*19/100),7,2,state.charge()<20?0xffe65e68:0xff53cd93);
        if(app.equals("home"))home(g);else{
            if(!app.equals("thread"))text(g,t("phone."+titleKey()).getString(),50,52,ink(),149);
            switch(app){
                case "contacts"->{if(filteredContacts(true).isEmpty())paragraph(g,t("phone.no_contacts"),28,190,190,8,muted());}
                case "messages"->{if(conversations().isEmpty())paragraph(g,t("phone.no_messages"),28,145,190,8,muted());}
                case "calls","recents"->{if(state.call()!=null)call(g);else if(app.equals("recents")&&state.personal().history().isEmpty())empty(g,"phone.no_calls",140);}
                case "thread"->messages(g);
                case "compose","add_contact"->paragraph(g,t("phone.add_help"),28,91,190,3,muted());
                case "contact"->{var c=selected();if(c!=null){avatar(g,c.name(),87,104,74);center(g,c.name(),124,192,ink(),192);center(g,PhoneNumbers.format(c.number()),124,213,muted(),195);}}
                case "mycard"->{avatar(g,minecraft.player==null?"?":minecraft.player.getGameProfile().name(),88,96,72);center(g,minecraft.player==null?"":minecraft.player.getGameProfile().name(),124,187,ink(),190);center(g,PhoneNumbers.format(state.number()),124,210,accent(),200);paragraph(g,t("phone.mycard_help"),28,354,190,6,muted());}
                case "share"->{text(g,t("phone.nearby_help").getString(),26,118,muted(),196);if(state.personal().nearby().isEmpty())empty(g,"phone.share_near",175);}
                case "offers"->{if(state.personal().offers().isEmpty())empty(g,"phone.no_requests",143);}
                case "confirm_delete"->paragraph(g,t("phone.delete_thread_help"),28,112,190,6,ink());
                case "gps"->map(g);
                case "battery"->{center(g,state.charge()+"%",124,126,accent(),180);rounded(g,54,162,140,45,11,muted());rounded(g,58,166,Math.max(1,state.charge()*132/100),37,8,0xff49bb83);center(g,model().battery+" mAh",124,226,ink(),190);paragraph(g,t("phone.battery_help"),28,253,190,5,muted());}
                case "about"->about(g);
                case "camera"->{g.fill(26,99,222,100,0x90ffffff);g.fill(26,365,222,366,0x90ffffff);g.fill(89,100,90,366,0x60ffffff);g.fill(158,100,159,366,0x60ffffff);center(g,"1×",124,382,0xffffffff,100);}
                case "photos"->{if(gallery.photos().isEmpty())empty(g,gallery.loading()?"phone.loading":gallery.failed()?"phone.photos_error":"phone.no_photos",145);}
                case "photo"->{var photos=gallery.photos();if(!photos.isEmpty()){photoIndex=Math.clamp(photoIndex,0,photos.size()-1);drawPhoto(g,photos.get(photoIndex),24,104,200,304);}}
                case "notes"->{if(state.personal().notes().isEmpty())empty(g,"phone.no_notes",126);}
                case "note_edit"->{rounded(g,24,153,200,178,16,surface());paragraph(g,Component.literal(noteDraft),35,166,178,15,ink());text(g,noteDraft.length()+" / 256",32,335,muted(),190);}
                case "calculator"->{rounded(g,24,94,200,94,18,surface());text(g,calculator.isEmpty()?"0":calculator,35,112,muted(),178);String result=answer.isEmpty()?"0":answer;large(g,result,124,151,ink(),Math.min(1.5f,180f/Math.max(1,font.width(result))));}
                case "notifications"->{if(unread()==0&&state.personal().offers().isEmpty())empty(g,"phone.no_notifications",220);}
                default->{}
            }
        }
        rounded(g,96,490,56,3,2,wallpaper?0xeeffffff:ink());
        g.pose().popMatrix();super.extractRenderState(g,mx,my,dt);
        if(toastTicks>0){g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale);rounded(g,22,455,204,29,12,PhoneClient.dark?0xff354258:0xffdce5f1);paragraph(g,toast,31,460,186,2,ink());g.pose().popMatrix();}
    }
    private String titleKey(){return switch(app){case "contact"->"contact_details";case "note_edit"->"notes";case "photo"->"photos";case "offers"->"share";case "confirm_delete"->"delete_thread";default->app;};}
    private void home(GuiGraphicsExtractor g){
        if(colorOS()){
            clockGlyphs(g,clock(),30,76,38,62);center(g,t("phone.game_day",day()).getString(),124,147,homeInk(),195);
            rounded(g,22,176,204,65,19,0x42ffffff);text(g,t("phone.weather").getString(),34,186,homeInk(),178);text(g,weather(),34,202,homeInk(),179);text(g,PhoneNumbers.format(state.number()),34,223,homeInk(),179);
        }else{
            rounded(g,22,78,95,126,23,0x3bffffff);clockGlyphs(g,clock().substring(0,2),29,88,39,46);clockGlyphs(g,clock().substring(3),29,141,39,46);
            rounded(g,124,78,102,57,19,0x42ffffff);text(g,t("phone.weather").getString(),134,89,homeInk(),82);text(g,weather(),134,111,homeInk(),82);
            rounded(g,124,143,102,61,19,0x42ffffff);text(g,t("phone.battery").getString(),134,153,homeInk(),82);text(g,state.charge()+"% · "+(state.airplane()?"OFF":"5G"),134,177,homeInk(),83);
            rounded(g,22,216,204,26,13,0x40ffffff);center(g,PhoneNumbers.format(state.number()),124,225,homeInk(),190);
        }
        rounded(g,colorOS()?24:20,419,colorOS()?200:208,64,colorOS()?24:21,colorOS()?0x50edf5ff:0x75f4f5ff);
        rounded(g,215,42,10,9,4,0xeeffffff);if(unread()+state.personal().offers().size()>0)rounded(g,221,38,6,6,3,0xffed525c);
        if(state.call()!=null){rounded(g,67,37,126,19,10,0xdd1e392f);center(g,state.call().active()?t("phone.active_call",state.call().name()).getString():t("phone.ringing_in").getString(),130,43,0xffffffff,114);}
        else{rounded(g,92,42,64,3,2,0x60ffffff);}
        text(g,model().os,26,250,homeInk(),196);
    }
    private void clockGlyphs(GuiGraphicsExtractor g,String value,int bx,int by,int width,int height){
        String texture=model()==PhoneModel.VIVO_X500_PRO_MAX?"clock_dark":"clock";for(int i=0;i<value.length();i++){int glyph=value.charAt(i)==':'?10:value.charAt(i)-'0';g.blit(Napuli.id("textures/phone/"+texture+".png"),bx+i*width,by,bx+(i+1)*width,by+height,glyph/11f,(glyph+1)/11f,0,1);}
    }
    private String weather(){return (minecraft.level!=null&&minecraft.level.dimension()==NapoliMap.LEVEL?"Napoli":t("phone.current_world").getString())+" · "+t(minecraft.level!=null&&minecraft.level.isRaining()?"phone.rain":"phone.clear").getString();}
    private long day(){return minecraft.level==null?1:Math.floorDiv(minecraft.level.getOverworldClockTime(),24000)+1;}
    private String clock(){return minecraft.level==null?"08:30":time(minecraft.level.getOverworldClockTime());}
    private static String time(long tick){long value=Math.floorMod(tick+6000,24000);return String.format(Locale.ROOT,"%02d:%02d",value/1000,value%1000*60/1000);}
    private void call(GuiGraphicsExtractor g){var c=state.call();avatar(g,c.name(),84,103,80);center(g,c.name(),124,204,ink(),196);var contact=selectedCallContact(c.peer());center(g,contact==null?"":PhoneNumbers.format(contact.number()),124,225,muted(),196);center(g,c.active()?String.format(Locale.ROOT,"%02d:%02d",c.seconds()/60,c.seconds()%60):t(c.incoming()?"phone.ringing_in":"phone.ringing_out").getString(),124,250,accent(),196);if(!PhoneAudio.error.isEmpty())paragraph(g,t(PhoneAudio.error),27,337,194,2,muted());}
    private PhonePacket.Contact selectedCallContact(UUID id){return state.contacts().stream().filter(c->c.id().equals(id)).findFirst().orElse(null);}
    private void messages(GuiGraphicsExtractor g){
        var pages=messagePages();page=Math.clamp(page,0,pages.size()-1);var entries=pages.get(page);int by=345-entries.stream().mapToInt(this::messageHeight).sum();
        for(var m:entries){int height=messageHeight(m)-5,bx=m.outgoing()?43:24;int color=m.outgoing()?(PhoneClient.dark?(colorOS()?0xff214d39:0xff283e70):(colorOS()?0xffd3eedf:0xffdce5ff)):surface();rounded(g,bx,by,181,height,13,color);paragraph(g,Component.literal(m.body()),bx+10,by+8,158,30,ink());text(g,time(m.time())+(m.outgoing()?(m.read()?"  ✓✓":"  ✓"):""),bx+10,by+height-11,muted(),160);by+=height+5;}
        if(entries.isEmpty())empty(g,"phone.no_messages",141);text(g,(page+1)+" / "+pages.size(),101,360,muted(),65);
    }
    private void map(GuiGraphicsExtractor g){
        rounded(g,24,115,200,143,15,PhoneClient.dark?0xff263e48:0xffdfece5);
        if(minecraft.player!=null)for(int row=0;row<128;row+=4)for(int col=0;col<192;col+=4){
            int rgb=NapoliRadar.terrainColor(minecraft.player.getBlockX()+Math.round((col-96)*1.4f),minecraft.player.getBlockZ()+Math.round((row-64)*1.4f));int r=(rgb>>16&255),green=(rgb>>8&255),b=rgb&255;if(!PhoneClient.dark){r=(r+240)/2;green=(green+248)/2;b=(b+240)/2;}g.fill(28+col,122+row,32+col,126+row,0xff000000|r<<16|green<<8|b);
        }
        text(g,"NAPOLI",37,124,0xff65857a,180);text(g,"N ↑",193,124,0xff52666b,27);
        if(minecraft.player!=null&&minecraft.level.dimension()==NapoliMap.LEVEL){var pos=minecraft.player.blockPosition();for(var p:state.places()){int dx=124+Math.round((p.position().getX()-pos.getX())/1.4f),dz=186+Math.round((p.position().getZ()-pos.getZ())/1.4f);if(dx>30&&dx<217&&dz>144&&dz<232)rounded(g,dx,dz,4,4,2,0xffaf7c6c);}if(state.waypoint()!=null){int dx=Math.clamp(124+Math.round((state.waypoint().getX()-pos.getX())/1.4f),31,215),dz=Math.clamp(186+Math.round((state.waypoint().getZ()-pos.getZ())/1.4f),145,232);line(g,124,186,dx,dz,0xff427ef1);rounded(g,dx-3,dz-3,7,7,3,0xffed9b3a);}rounded(g,119,181,11,11,5,0xffffffff);rounded(g,121,183,7,7,3,0xff347eee);}
        text(g,minecraft.player==null?"":minecraft.player.blockPosition().toShortString(),28,246,0xff43616d,192);text(g,state.goal().isEmpty()?t("phone.pick_destination").getString():state.instruction().getString(),25,267,muted(),198);
    }
    private void about(GuiGraphicsExtractor g){
        var m=model();String[] rows={m.name,m.os,"CPU · "+m.cpu,"GPU · "+m.gpu,"RAM · "+m.ram,m.storage,m.display,m.battery+" mAh",t("phone.charging").getString()+" · "+m.charging,m.cameras,"5G · Wi-Fi 7 · Bluetooth 6.0","GPS · NFC · USB-C"};
        for(int i=page*6;i<Math.min(page*6+6,rows.length);i++){int by=94+(i-page*6)*54;rounded(g,24,by,200,47,13,surface());paragraph(g,Component.literal(rows[i]),34,by+12,180,3,ink());}center(g,(page+1)+" / 2",124,444,muted(),80);
    }
    private void calculate(String key){
        switch(key){case "C"->{calculator="";answer="";}case "⌫"->{if(!calculator.isEmpty())calculator=calculator.substring(0,calculator.length()-1);}case "±"->{int start=calculator.length();while(start>0&&(Character.isDigit(calculator.charAt(start-1))||calculator.charAt(start-1)=='.'||calculator.charAt(start-1)=='%'))start--;boolean negative=start>0&&calculator.charAt(start-1)=='-'&&(start==1||"+−×÷-*/".indexOf(calculator.charAt(start-2))>=0);calculator=negative?calculator.substring(0,start-1)+calculator.substring(start):calculator.substring(0,start)+"-"+calculator.substring(start);}case "="->{try{answer=PhoneCalculator.calculate(calculator);calculator=answer;}catch(RuntimeException invalid){answer=t("phone.calculation_error").getString();}}default->{if(calculator.length()<64)calculator+=key;}}
    }
    private void photo(){minecraft.gui.setScreen(null);PhonePhoto.capture(this);}
    private void drawPhoto(GuiGraphicsExtractor g,PhoneGallery.Photo photo,int bx,int by,int width,int height){float factor=Math.min(width/(float)photo.width(),height/(float)photo.height());int w=Math.round(photo.width()*factor),h=Math.round(photo.height()*factor),left=bx+(width-w)/2,top=by+(height-h)/2;g.blit(photo.texture(),left,top,left+w,top+h,0,1,0,1);}
    private void avatar(GuiGraphicsExtractor g,String name,int bx,int by,int size){rounded(g,bx,by,size,size,size/2,PhoneClient.dark?0xff3d526e:0xffdce9f9);large(g,name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT),bx+size/2,by+size/2-5,0xff356292,size>50?2.2f:1.2f);}
    private void empty(GuiGraphicsExtractor g,String key,int by){paragraph(g,t(key),29,by,188,10,muted());}
    private void text(GuiGraphicsExtractor g,String value,int tx,int ty,int color,int max){g.text(font,NapoliScreen.fit(font,value,max),tx,ty,color,false);}
    private void center(GuiGraphicsExtractor g,String value,int cx,int cy,int color,int max){String fit=NapoliScreen.fit(font,value,max);g.text(font,fit,cx-font.width(fit)/2,cy,color,false);}
    private void large(GuiGraphicsExtractor g,String value,int cx,int cy,int color,float factor){String fit=NapoliScreen.fit(font,value,(int)(194/factor));g.pose().pushMatrix();g.pose().translate(cx,cy);g.pose().scale(factor);g.text(font,fit,-font.width(fit)/2,0,color,false);g.pose().popMatrix();}
    private void paragraph(GuiGraphicsExtractor g,Component value,int tx,int ty,int max,int rows,int color){var lines=font.split(value,max);for(int i=0;i<Math.min(rows,lines.size());i++)g.text(font,lines.get(i),tx,ty+i*10,color,false);}
    static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int color){for(int row=0;row<h;row++){int edge=0;if(row<r)edge=(int)Math.ceil(r-Math.sqrt(r*r-Math.pow(r-row-.5,2)));else if(row>=h-r)edge=(int)Math.ceil(r-Math.sqrt(r*r-Math.pow(row-(h-r)+.5,2)));g.fill(x+edge,y+row,x+w-edge,y+row+1,color);}}
    private static void line(GuiGraphicsExtractor g,int x,int y,int dx,int dy,int color){int n=Math.max(Math.abs(dx-x),Math.abs(dy-y));for(int i=0;i<=n;i++){int px=x+(dx-x)*i/Math.max(1,n),py=y+(dy-y)*i/Math.max(1,n);g.fill(px,py,px+3,py+3,color);}}
}
