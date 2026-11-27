package dev.napuli.client;

import dev.napuli.CityVehicle;
import dev.napuli.Napuli;
import dev.napuli.VehicleCabin;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

final class CityVehicleRenderer extends MobRenderer<CityVehicle,CityVehicleRenderer.State,CityVehicleRenderer.Model> {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Napuli.id("city_vehicle"),"main");
    private static final String[] COLORS={"car_blue","car_red","car_cream","car_green"};
    static final class State extends LivingEntityRenderState {int kind,color;boolean police,taxi;float doors,steering;}
    CityVehicleRenderer(EntityRendererProvider.Context context){super(context,new Model(context.bakeLayer(LAYER)),1.25f);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(CityVehicle vehicle,State state,float delta){
        super.extractRenderState(vehicle,state,delta);state.kind=vehicle.kind();state.police=vehicle.policeVehicle();state.taxi=vehicle.taxi();
        state.color=Math.floorMod(vehicle.getUUID().hashCode(),COLORS.length);state.doors=vehicle.doorOpening(delta);
        state.steering=net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(vehicle.getYRot()-vehicle.yRotO)*.07f,-.4f,.4f);
    }
    @Override public Identifier getTextureLocation(State s){return Napuli.id("textures/entity/vehicles/"+(s.kind==1?"bus":s.kind==2?"tram":s.kind==3?"metro":s.police?"police":s.taxi?"taxi":COLORS[s.color])+".png");}
    static LayerDefinition layer(){
        var mesh=new MeshDefinition();part=0;for(int kind=0;kind<4;kind++)body(mesh.getRoot().addOrReplaceChild("kind"+kind,CubeListBuilder.create(),PartPose.ZERO),kind);
        return LayerDefinition.create(mesh,512,512);
    }
    private static final int PAINT=0,GLASS=1,RUBBER=2,FABRIC=3,METAL=4,FLOOR=5,LAMP=6,BLUE=7,RED=8,AMBER=9;
    private static int part;
    private static void box(PartDefinition root,int material,double x,double y,double z,double w,double h,double d){
        root.addOrReplaceChild("p"+part++,CubeListBuilder.create().texOffs(material>=8?(material-8)*128:material%2*256,material>=8?96:material/2*128).addBox((float)(-(x+w)*16),(float)(24-(y+h)*16),(float)(-(z+d)*16),(float)(w*16),(float)(h*16),(float)(d*16)),PartPose.ZERO);
    }
    private static void body(PartDefinition root,int kind){
        var l=VehicleCabin.layout(kind);double w=l.width(),half=w/2,front=l.length()/2,back=-front,roof=l.height(),floor=l.floor();
        // Panels are segmented to keep every face inside one material tile.
        for(double z=back;z<front-.01;z+=1.5){double d=Math.min(1.5,front-z);
            box(root,FLOOR,-half,floor-.13,z,w,.13,d);box(root,METAL,-half,.18,z,w,.12,d);
            if(kind>0){box(root,PAINT,-half,roof-.15,z,w,.15,d);box(root,FABRIC,-half+.12,roof-.19,z,w-.24,.035,d);
                for(int side:new int[]{-1,1}){
                    double x=side<0?-half:half-.08;
                    var cuts=new java.util.TreeSet<Double>();cuts.add(z);cuts.add(z+d);
                    if(side>0)for(double cut:new double[]{front-1.22,front-.29,back+.65,back+1.58})if(cut>z&&cut<z+d)cuts.add(cut);
                    Double previous=null;for(double end:cuts){if(previous!=null){double middle=(previous+end)/2;boolean doorway=side>0&&(middle>front-1.22&&middle<front-.29||middle>back+.65&&middle<back+1.58);
                        if(!doorway){box(root,PAINT,x,floor,previous,.08,.5,end-previous);box(root,GLASS,x,1.05,previous,.045,1.3,end-previous);box(root,METAL,x,2.38,previous,.08,.09,end-previous);}
                    }previous=end;}
                }
                box(root,LAMP,-.05,roof-.205,z,.1,.02,d*.75);
            }
        }
        if(kind==0){
            // Engine and boot are solid; the passenger compartment stays hollow.
            box(root,PAINT,-half,.42,.98,w,.5,front-.98);box(root,PAINT,-half,.42,back,w,.5,.75);
            box(root,PAINT,-half+.08,1.84,-1.48,w-.16,.11,2.35);box(root,FABRIC,-half+.15,1.79,-1.4,w-.3,.045,2.2);
            var windshield=root.addOrReplaceChild("windshield",CubeListBuilder.create().texOffs(256,0).addBox((float)(-(w-.24)*8),-11.52f,0,(float)((w-.24)*16),11.52f,.6f),PartPose.offsetAndRotation(0,7.2f,-15.2f,-.3f,0,0));
            for(int s:new int[]{-1,1})windshield.addOrReplaceChild("pillar"+s,CubeListBuilder.create().texOffs(0,0).addBox((float)(s*(half-.12)*16)-.6f,-12.2f,-.05f,1.2f,12.2f,1.2f),PartPose.ZERO);
            box(root,GLASS,-half+.12,1.08,-1.5,w-.24,.66,.035);
            for(int side:new int[]{-1,1}){
                double x=side<0?-half:half-.09;box(root,PAINT,x,.35,-1.5,.09,.48,2.5);
                for(double z:new double[]{-1.48,-.36})box(root,PAINT,x,.83,z,.09,1.05,.075);
                var door=root.addOrReplaceChild("door"+side,CubeListBuilder.create(),PartPose.offset((float)(-side*half*16),0,(float)(-.91*16)));
                double doorX=side<0?0:-.075;
                box(door,PAINT,doorX,.5,-1.15,.075,.49,1.15);box(door,GLASS,doorX,1,-1.15,.035,.75,1.1);box(door,METAL,doorX,.91,-.98,.09,.035,.2);
                box(root,GLASS,x,1,-1.42,.035,.75,.98);
                box(root,METAL,side<0?-half-.15:half,1.13,.62,.17,.15,.27);box(root,GLASS,side<0?-half-.16:half+.14,1.15,.63,.025,.1,.23);
            }
            box(root,METAL,-half+.12,.82,.86,w-.24,.35,.2);box(root,RUBBER,-.8,1.17,.85,.53,.04,.25);
            box(root,BLUE,-.7,1.213,.86,.12,.012,.11);box(root,LAMP,-.5,1.213,.86,.12,.012,.11);
            box(root,METAL,-.12,.52,-.55,.24,.25,1.25);box(root,RUBBER,-.09,.79,-.35,.18,.06,.18);
            box(root,METAL,-.03,.81,-.32,.04,.2,.04);box(root,RUBBER,-.06,1,-.35,.1,.07,.1);steering(root,-.48,1.13,.78);
            var beacon=root.addOrReplaceChild("beacon",CubeListBuilder.create(),PartPose.ZERO);
            box(beacon,METAL,-.65,roof,-.3,1.3,.06,.32);for(double x:new double[]{-.63,.35})box(beacon,BLUE,x,roof+.06,-.3,.28,.15,.32);
            var taxi=root.addOrReplaceChild("taxi",CubeListBuilder.create(),PartPose.ZERO);box(taxi,LAMP,-.32,roof,-.35,.64,.2,.28);
        }else{
            for(double z=back+.2;z<front;z+=1.5)for(int s:new int[]{-1,1}){if(s>0&&(z>front-1.22&&z<front-.29||z>back+.65&&z<back+1.58))continue;box(root,PAINT,s<0?-half:half-.09,.93,z,.09,roof-.93,.075);}
            for(double z:new double[]{back,front-.08}){
                box(root,PAINT,-half,floor,z,w,.6,.08);box(root,GLASS,-half+.12,1.13,z,w-.24,1.42,.04);box(root,PAINT,-half,2.58,z,w,roof-2.58,.08);
                for(double x:new double[]{-half,half-.12})box(root,PAINT,x,1.06,z,.12,1.52,.1);
            }
            for(int i=0;i<2;i++){
                double z=i==0?front-1.22:back+.65;var door=root.addOrReplaceChild("door"+i,CubeListBuilder.create(),PartPose.ZERO);
                box(door,METAL,half-.1,.48,z,.1,.06,.93);box(door,GLASS,half-.11,1.05,z,.05,1.36,.93);box(door,PAINT,half-.13,.54,z,.11,.48,.93);
                for(double end:new double[]{z,z+.9})box(door,METAL,half-.14,.54,end,.12,2.05,.035);box(root,LAMP,half-.17,.48,z,.1,.035,.9);
            }
            box(root,METAL,-half+.2,.7,front-.65,w-.4,.48,.42);steering(root,-.7,1.18,front-.8);
            seat(root,new Vec3(-.72,.08,front-1.3),true);box(root,GLASS,-half+.12,1.1,front-1.63,half-.35,1.45,.04);
            for(double z=back+.8;z<front-1.5;z+=1.1)for(int side:new int[]{-1,1}){box(root,METAL,side*.36-.025,floor,z,.05,2.17,.05);box(root,BLUE,side*.36-.03,2.2,z-.08,.06,.16,.16);}
            for(int side:new int[]{-1,1})for(double z=back+.5;z<front-1;z+=1.5){double d=Math.min(1.5,front-1-z);box(root,METAL,side*.36-.025,2.65,z,.05,.05,d);}
            if(kind==2){box(root,METAL,-.6,roof,-.5,1.2,.14,1);box(root,METAL,-.48,roof+.14,-.4,.06,.38,.08);box(root,METAL,.42,roof+.14,-.4,.06,.38,.08);box(root,METAL,-.65,roof+.52,-.44,1.3,.06,.15);}
            if(kind==3)for(double z=back+1;z<front-1;z+=2)box(root,METAL,-.65,roof,z,1.3,.18,.7);
            box(root,RUBBER,-.67,2.64,front+.005,1.34,.26,.03);box(root,LAMP,-.52,2.72,front+.04,1.04,.055,.012);
        }
        for(var at:l.seats())seat(root,at,kind>0);
        double axle=front-(kind==0?.85:1.6),radius=kind==0?.35:.43;
        for(int i=0;i<4;i++){
            double x=(i%2==0?-1:1)*(half-.07),z=i<2?axle:-axle;
            var wheel=root.addOrReplaceChild("wheel"+i,CubeListBuilder.create(),PartPose.offset((float)(-x*16),(float)(24-radius*16),(float)(-z*16)));
            // The wheel pivot is already at its axle rather than the vehicle origin.
            float r=(float)(radius*16);
            wheel.addOrReplaceChild("tire",CubeListBuilder.create().texOffs(0,128).addBox(-2.4f,-r*.7f,-r,4.8f,r*1.4f,r*2).addBox(-2.4f,-r,-r*.7f,4.8f,r*2,r*1.4f),PartPose.ZERO);
            wheel.addOrReplaceChild("hub",CubeListBuilder.create().texOffs(0,256).addBox(i%2==0?2.4f:-2.7f,(float)(-radius*10),(float)(-radius*10),.3f,(float)(radius*20),(float)(radius*20)),PartPose.ZERO);
        }
        box(root,RUBBER,-half,.32,front-.03,w,.16,.13);box(root,RUBBER,-half,.32,back-.08,w,.16,.13);
        if(kind==0)box(root,RUBBER,-.55,.59,front+.025,1.1,.16,.03);
        for(int side:new int[]{-1,1}){double x=side<0?-half+.12:half-.47;box(root,LAMP,x,.67,front+.01,.35,.18,.035);box(root,RED,x,.72,back-.035,.35,.17,.035);box(root,AMBER,x,.57,front+.015,.35,.07,.035);}
        box(root,LAMP,-.24,.48,front+.09,.48,.1,.015);box(root,LAMP,-.24,.48,back-.10,.48,.1,.015);
    }
    private static void seat(PartDefinition root,Vec3 at,boolean coach){
        double x=at.x,y=at.y+.72,z=at.z;
        box(root,METAL,x-.22,y-.35,z-.2,.44,.35,.4);box(root,FABRIC,x-.28,y,z-.32,.56,.12,.6);
        box(root,FABRIC,x-.28,y+.12,z-.37,.56,coach?.72:.59,.13);box(root,FABRIC,x-.19,y+.72,z-.36,.38,.2,.12);
    }
    private static void steering(PartDefinition root,double x,double y,double z){
        var wheel=root.addOrReplaceChild("steering",CubeListBuilder.create(),PartPose.ZERO);
        box(wheel,RUBBER,x-.21,y,z,.42,.045,.04);box(wheel,RUBBER,x-.21,y+.28,z,.42,.045,.04);
        for(double side:new double[]{-.21,.17})box(wheel,RUBBER,x+side,y,z,.04,.3,.04);box(wheel,METAL,x-.02,y+.1,z,.04,.08,.19);
    }
    static final class Model extends EntityModel<State>{
        Model(ModelPart root){super(root,RenderTypes::entityTranslucent);}
        @Override public void setupAnim(State s){
            super.setupAnim(s);for(int kind=0;kind<4;kind++)root.getChild("kind"+kind).visible=kind==s.kind;
            var body=root.getChild("kind"+s.kind);
            if(s.kind==0){body.getChild("beacon").visible=s.police;body.getChild("taxi").visible=s.taxi;for(int side:new int[]{-1,1})body.getChild("door"+side).yRot=side*s.doors*1.05f;}
            else for(int i=0;i<2;i++)body.getChild("door"+i).z=(i==0?1:-1)*s.doors*11;
            for(int i=0;i<4;i++){var wheel=body.getChild("wheel"+i);wheel.xRot=s.walkAnimationPos*2;wheel.yRot=i<2?s.steering:0;}
        }
    }
}
