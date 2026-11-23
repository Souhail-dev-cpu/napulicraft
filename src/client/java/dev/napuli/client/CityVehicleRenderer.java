package dev.napuli.client;

import dev.napuli.CityVehicle;
import dev.napuli.Napuli;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

final class CityVehicleRenderer extends MobRenderer<CityVehicle,CityVehicleRenderer.State,CityVehicleRenderer.Model> {
    static final ModelLayerLocation LAYER=new ModelLayerLocation(Napuli.id("city_vehicle"),"main");
    static final class State extends LivingEntityRenderState {int kind;}
    CityVehicleRenderer(EntityRendererProvider.Context context){super(context,new Model(context.bakeLayer(LAYER)),1f);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(CityVehicle vehicle,State state,float delta){super.extractRenderState(vehicle,state,delta);state.kind=vehicle.kind();}
    @Override public Identifier getTextureLocation(State s){return Napuli.id("textures/entity/city_vehicle.png");}
    static LayerDefinition layer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("chassis",CubeListBuilder.create().texOffs(0,0).addBox(-10,14,-18,20,7,36),PartPose.ZERO);
        root.addOrReplaceChild("cabin",CubeListBuilder.create().texOffs(0,48).addBox(-9,3,-10,18,11,23),PartPose.ZERO);
        for(int i=0;i<4;i++)root.addOrReplaceChild("wheel"+i,CubeListBuilder.create().texOffs(0,96).addBox(-2,-3,-3,4,6,6),PartPose.offset(i%2==0?-10:10,21,i/2==0?-12:12));
        root.addOrReplaceChild("lights",CubeListBuilder.create().texOffs(40,96).addBox(-8,15,-19,4,3,1).addBox(4,15,-19,4,3,1),PartPose.ZERO);
        return LayerDefinition.create(mesh,128,128);
    }
    static final class Model extends EntityModel<State>{
        Model(ModelPart root){super(root);}
        @Override public void setupAnim(State s){super.setupAnim(s);root.zScale=s.kind==0?1:1.7f;root.yScale=s.kind==0?1:1.2f;}
    }
}
