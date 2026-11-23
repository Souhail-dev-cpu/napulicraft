package dev.napuli.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.napuli.Munaciello;
import dev.napuli.MunacielloGigante;
import dev.napuli.Napuli;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import dev.napuli.Motorino;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class NapuliClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(Napuli.MUNACIELLO, MunacielloRenderer::new);
		EntityRendererRegistry.register(Napuli.GIGANTE, GiganteRenderer::new);
		ModelLayerRegistry.registerModelLayer(MotorinoModel.LAYER, MotorinoModel::createLayer);
		EntityRendererRegistry.register(Napuli.MOTORINO, MotorinoRenderer::new);
		ModelLayerRegistry.registerModelLayer(CityVehicleRenderer.LAYER,CityVehicleRenderer::layer);
		EntityRendererRegistry.register(Napuli.CITY_VEHICLE,CityVehicleRenderer::new);
		EntityRendererRegistry.register(Napuli.CIABATTA, ThrownItemRenderer::new);
		ModelLayerRegistry.registerModelLayer(NapoliResidentModel.LAYER, NapoliResidentModel::layer);
		EntityRendererRegistry.register(Napuli.RESIDENT, ResidentRenderer::new);
		RadioClient.register();
		HudClient.register();
		NapoliNoticeToast.register();
		FootballScreen.register();
		EmploymentScreen.register();
		TelevisionScreen.register();
		dev.napuli.LivingCityTest.register();
		AutoTest.register();
		CityTest.register();
		PoliceTest.register();
		MapTest.register();
		RadioTest.register();
		CentroTest.register();
		SpawnTest.register();
		dev.napuli.TaxVisitTest.register();
		dev.napuli.PeppeTest.register();
		dev.napuli.MotorinoTest.register();
		KitchenScreens.register();
		dev.napuli.KitchenTest.register();
		dev.napuli.PoliceAITest.register();
	}

	/** Small hooded monk, holds what he stole in his hand (so you see your diamond pick running away). */
	static class MunacielloRenderer extends HumanoidMobRenderer<Munaciello, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
		static final Identifier TEX = Napuli.id("textures/entity/munaciello.png");

		MunacielloRenderer(EntityRendererProvider.Context ctx) {
			super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.35f);
		}

		@Override public Identifier getTextureLocation(HumanoidRenderState s) { return TEX; }
		@Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
		@Override protected void scale(HumanoidRenderState s, PoseStack pose) { pose.scale(0.72f, 0.72f, 0.72f); }
	}

	/** The giant (size comes from its SCALE attribute) with glowing red eyes and grin. */
	static class GiganteRenderer extends HumanoidMobRenderer<MunacielloGigante, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
		static final Identifier TEX = Napuli.id("textures/entity/munaciello_gigante.png");
		static final RenderType EYES = RenderTypes.eyes(Napuli.id("textures/entity/munaciello_gigante_eyes.png"));

		GiganteRenderer(EntityRendererProvider.Context ctx) {
			super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
			this.addLayer(new EyesLayer<>(this) {
				@Override public RenderType renderType() { return EYES; }
			});
		}

		@Override public Identifier getTextureLocation(HumanoidRenderState s) { return TEX; }
		@Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
	}

	static class ResidentRenderer extends HumanoidMobRenderer<dev.napuli.NapoliResident, NapoliResidentModel.State, NapoliResidentModel> {
		private static final java.util.Map<String, Identifier> TEXTURES = new java.util.HashMap<>();
		ResidentRenderer(EntityRendererProvider.Context ctx) {
			super(ctx, new NapoliResidentModel(ctx.bakeLayer(NapoliResidentModel.LAYER)), 0.45f);
			this.addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this));
			// so the casco (and any helmet) shows on the boys of the scooter
			this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this, net.minecraft.client.renderer.entity.ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, ctx.getModelSet(),
					(net.minecraft.client.model.geom.ModelPart p) -> new HumanoidModel<NapoliResidentModel.State>(p)), ctx.getEquipmentRenderer()));
		}

		/** passers-by and carabinieri have several looks: texture "passerby_3", "carabiniere_1"... */
		static Identifier texture(dev.napuli.NapoliResident.Role role, int variant) {
			String name = switch (role) {
				case FAMILY_MOTHER -> "carmela";
				case CIVIC_WOMAN -> variant==10 ? "influencer" : "civic_woman_" + Math.floorMod(variant,10);
				case CIVIC_MAN -> variant==10 ? "influencer" : "civic_man_" + Math.floorMod(variant,10);
				case FAMILY_FATHER -> "tifoso";
				case FAMILY_CHILD -> "scugnizzo";
				case FAMILY_CHILD_TWO -> "passerby_3";
				default -> role.variants() > 1 ? role.id() + "_" + Math.floorMod(variant, role.variants()) : role.id();
			};
			return TEXTURES.computeIfAbsent(name, n -> Napuli.id("textures/entity/" + n + ".png"));
		}

		@Override public Identifier getTextureLocation(NapoliResidentModel.State state) { return texture(state.role, state.variant); }
		@Override public NapoliResidentModel.State createRenderState() { return new NapoliResidentModel.State(); }
		@Override
		protected void scale(NapoliResidentModel.State s, PoseStack pose) {
			if (s.role == dev.napuli.NapoliResident.Role.SCUGNIZZO || s.role == dev.napuli.NapoliResident.Role.FAMILY_CHILD
					|| s.role == dev.napuli.NapoliResident.Role.FAMILY_CHILD_TWO) pose.scale(0.74f, 0.74f, 0.74f); // 'o scugnizzo is a kid
		}
		@Override
		protected int getBlockLightLevel(dev.napuli.NapoliResident npc, net.minecraft.core.BlockPos pos) {
			return npc.role().spirit() ? 13 : super.getBlockLightLevel(npc, pos);
		}

		@Override
		public void extractRenderState(dev.napuli.NapoliResident npc, NapoliResidentModel.State state, float partialTick) {
			super.extractRenderState(npc, state, partialTick);
			state.role = npc.role();
			state.variant = npc.variant();
			state.activity = npc.activity();
			if (npc.getVehicle() instanceof Motorino bike) {
				state.bodyRot = bike.getYRot(partialTick);
				state.yRot = 0;
				state.xRot = 0;
			}
		}
	}

	static class MotorinoRenderer extends MobRenderer<Motorino, LivingEntityRenderState, MotorinoModel> {
		static final Identifier TEX = Napuli.id("textures/entity/motorino.png");

		MotorinoRenderer(EntityRendererProvider.Context ctx) { super(ctx, new MotorinoModel(ctx.bakeLayer(MotorinoModel.LAYER)), 0.5f); }

		@Override public Identifier getTextureLocation(LivingEntityRenderState s) { return TEX; }
		@Override public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }
	}
}
