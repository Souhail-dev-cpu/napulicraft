package dev.napuli.client;

import dev.napuli.NapoliResident;
import dev.napuli.NapoliResident.Role;
import dev.napuli.Napuli;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * One humanoid model for all the city people, with many hats, hairstyles and props that are switched on per role
 * (and per variant for passers-by and carabinieri), so nobody shares the same silhouette.
 * Women get slimmer arms. Texture is 128x128; UVs must match tools/GenResidentTextures.java.
 */
final class NapoliResidentModel extends HumanoidModel<NapoliResidentModel.State> {
	static final ModelLayerLocation LAYER = new ModelLayerLocation(Napuli.id("residente"), "main");

	private final ModelPart curls, ponytail, cap, scarf, fringe, bob, braid, curlsTop, earrings, hood, bag, waves, sidePart;
	private final ModelPart peppePortrait;
	private final ModelPart toque, coppola, mandolin, headscarf, longHair, bun, hairband, skirt, ccCap, beret, bandana;

	NapoliResidentModel(ModelPart root) {
		super(root);
		peppePortrait = head.getChild("peppe_portrait");
		curls = head.getChild("curls"); ponytail = head.getChild("ponytail"); cap = head.getChild("cap");
		scarf = body.getChild("scarf"); fringe = head.getChild("fringe"); bob = head.getChild("bob");
		braid = head.getChild("braid"); curlsTop = head.getChild("curls_top"); earrings = head.getChild("earrings");
		hood = body.getChild("hood"); bag = body.getChild("bag"); waves = head.getChild("waves"); sidePart = head.getChild("side_part");
		toque = head.getChild("toque"); coppola = head.getChild("coppola"); mandolin = body.getChild("mandolin");
		headscarf = head.getChild("headscarf"); longHair = head.getChild("long_hair"); bun = head.getChild("bun");
		hairband = head.getChild("hairband"); skirt = body.getChild("skirt"); ccCap = head.getChild("cc_cap");
		beret = head.getChild("beret"); bandana = head.getChild("bandana");
	}

	static LayerDefinition layer() {
		var mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
		var head = mesh.getRoot().getChild("head");
		var torso = mesh.getRoot().getChild("body");
		// ---- original parts (UVs unchanged)
		head.addOrReplaceChild("curls", CubeListBuilder.create().texOffs(0, 34).addBox(-4.3f, -8.9f, -4.3f, 8.6f, 3, 8.6f)
				.texOffs(0, 47).addBox(-4.2f, -6.0f, 3, 8.4f, 3.5f, 1.5f), PartPose.ZERO);
		head.addOrReplaceChild("ponytail", CubeListBuilder.create().texOffs(0, 55).addBox(-1.5f, -6.5f, 4, 3, 7, 2), PartPose.ZERO);
		head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 34).addBox(-4.3f, -8.7f, -4.2f, 8.6f, 3, 8.4f)
				.texOffs(0, 47).addBox(-3.7f, -6.1f, -6.7f, 7.4f, 1, 3), PartPose.ZERO);
		torso.addOrReplaceChild("scarf", CubeListBuilder.create().texOffs(35, 34).addBox(-4.1f, 0.3f, -2.15f, 8.2f, 2, 4.3f)
				.texOffs(35, 43).addBox(-1, 2, -2.4f, 2, 6, 1), PartPose.ZERO);
		head.addOrReplaceChild("fringe", CubeListBuilder.create().texOffs(64, 0).addBox(-4.1f, -8.3f, -4.6f, 5, 2, 1)
				.texOffs(76, 0).addBox(-4.1f, -7.3f, -4.6f, 2, 2, 1), PartPose.ZERO);
		head.addOrReplaceChild("bob", CubeListBuilder.create().texOffs(64, 5).addBox(-4.6f, -7, -3.9f, 1, 6, 8)
				.texOffs(64, 5).addBox(3.6f, -7, -3.9f, 1, 6, 8).texOffs(84, 5).addBox(-4.4f, -7, 3.8f, 8.8f, 7, 1), PartPose.ZERO);
		head.addOrReplaceChild("braid", CubeListBuilder.create().texOffs(64, 23).addBox(2.7f, -5, 3.4f, 2, 5, 2)
				.texOffs(72, 23).addBox(3, -0.5f, 3.7f, 1.6f, 5, 1.6f), PartPose.ZERO);
		head.addOrReplaceChild("curls_top", CubeListBuilder.create().texOffs(64, 34).addBox(-4.4f, -9.5f, -2.7f, 3, 2, 4)
				.texOffs(64, 34).addBox(-1.8f, -10.1f, -4.0f, 4, 3, 5).texOffs(64, 34).addBox(1.8f, -9.3f, -1.6f, 3, 2, 5), PartPose.ZERO);
		head.addOrReplaceChild("earrings", CubeListBuilder.create().texOffs(84, 24).addBox(-5, -2.8f, -1.4f, 1, 1, 1)
				.texOffs(84, 24).addBox(4, -2.8f, -1.4f, 1, 1, 1), PartPose.ZERO);
		torso.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(86, 31).addBox(-3.6f, 0.2f, 2, 7, 4, 2), PartPose.ZERO);
		torso.addOrReplaceChild("bag", CubeListBuilder.create().texOffs(86, 40).addBox(1, 6.3f, -3.3f, 4, 4, 2)
				.texOffs(100, 40).addBox(1, 7.2f, -3.5f, 4, 1, 1), PartPose.ZERO);
		head.addOrReplaceChild("waves", CubeListBuilder.create().texOffs(64, 48).addBox(-4.3f, -8.7f, -3.8f, 8.6f, 2.6f, 8.4f)
				.texOffs(64, 23).addBox(-4.4f, -7.5f, -4.1f, 2, 3.2f, 1.2f).texOffs(72, 23).addBox(3.4f, -7.4f, -3.6f, 1.3f, 4.4f, 2.0f), PartPose.ZERO);
		head.addOrReplaceChild("side_part", CubeListBuilder.create().texOffs(92, 48).addBox(-4.4f, -8.4f, -4.15f, 8.6f, 2.1f, 8.3f)
				.texOffs(76, 0).addBox(1.0f, -8.0f, -4.5f, 3.4f, 2.2f, 1.2f), PartPose.ZERO);
		// ---- new parts (all in the lower half of the 128x128 texture)
		head.addOrReplaceChild("toque", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5f, -14.5f, -3.5f, 7, 6, 7)
				.texOffs(0, 78).addBox(-4.2f, -9.2f, -4.2f, 8.4f, 1.2f, 8.4f), PartPose.ZERO);               // pizzaiolo hat
		head.addOrReplaceChild("coppola", CubeListBuilder.create().texOffs(36, 64).addBox(-4.4f, -8.9f, -4.4f, 8.8f, 2, 8.8f)
				.texOffs(36, 76).addBox(-4.0f, -7.3f, -6.8f, 8, 1, 2.4f), PartPose.ZERO);                  // flat cap
		torso.addOrReplaceChild("mandolin", CubeListBuilder.create().texOffs(74, 64).addBox(-2.5f, 1, 2.2f, 5, 7, 1.5f)
				.texOffs(74, 74).addBox(-0.5f, -4.5f, 2.4f, 1, 5.5f, 1), PartPose.rotation(0, 0, 0.35f)); // on the back
		head.addOrReplaceChild("headscarf", CubeListBuilder.create().texOffs(90, 64).addBox(-4.5f, -8.7f, -4.5f, 9, 4.5f, 9), PartPose.ZERO);
		head.addOrReplaceChild("long_hair", CubeListBuilder.create().texOffs(0, 90).addBox(-4.3f, -8.4f, 3.2f, 8.6f, 10, 1.6f), PartPose.ZERO);
		head.addOrReplaceChild("bun", CubeListBuilder.create().texOffs(22, 90).addBox(-1.5f, -10.8f, 1.2f, 3, 3, 3), PartPose.ZERO);
		head.addOrReplaceChild("hairband", CubeListBuilder.create().texOffs(36, 90).addBox(-4.4f, -8.3f, -4.4f, 8.8f, 1, 8.8f), PartPose.ZERO);
		torso.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(74, 90).addBox(-4.5f, 9, -2.6f, 9, 4, 5.2f), PartPose.ZERO);
		head.addOrReplaceChild("cc_cap", CubeListBuilder.create().texOffs(0, 104).addBox(-4.5f, -10.6f, -4.5f, 9, 3.2f, 9)
				.texOffs(0, 117).addBox(-3.5f, -7.4f, -6.3f, 7, 0.8f, 2), PartPose.ZERO);                  // carabiniere cap + visor
		head.addOrReplaceChild("beret", CubeListBuilder.create().texOffs(40, 104).addBox(-4.6f, -9.3f, -4.6f, 9.2f, 1.6f, 9.2f), PartPose.rotation(0, 0, -0.12f));
		head.addOrReplaceChild("bandana", CubeListBuilder.create().texOffs(80, 104).addBox(-4.4f, -8.7f, -4.4f, 8.8f, 2, 8.8f), PartPose.ZERO);
		// A narrower face with double-resolution texels, receding curls and a small raised nose.
		head.addOrReplaceChild("peppe_portrait", CubeListBuilder.create()
				.texOffs(0, 64).addBox(-7, -20, -7, 14, 20, 14)
				.texOffs(58, 64).addBox(-1.5f, -10, -8.2f, 3, 5, 2)
				.texOffs(74, 64).addBox(-9.2f, -15.5f, -3.1f, 3.5f, 8, 8)
				.texOffs(74, 64).addBox(5.7f, -15, -3.6f, 3.2f, 8, 8)
				.texOffs(74, 64).addBox(-8.8f, -9.1f, 0, 3.5f, 4.5f, 6)
				.texOffs(74, 64).addBox(5.9f, -9.2f, 0.9f, 3, 4, 6)
				.texOffs(74, 64).addBox(-5.4f, -20.9f, -2.4f, 3.5f, 2.6f, 5)
				.texOffs(74, 64).addBox(1.8f, -20.4f, 1.5f, 3.7f, 2.6f, 4)
				.texOffs(74, 64).addBox(-7.3f, -17.7f, 5.5f, 14.3f, 6.0f, 2.8f),
				PartPose.ZERO.withScale(0.5f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	/** which extra parts each look shows */
	static Set<String> parts(Role role, int variant) {
		return switch (role) {
			case GENNARO -> Set.of("cap");
			case CIRO -> Set.of("curls", "hood");
			case ENZO -> Set.of("fringe", "curls_top");
			case TERESA -> Set.of("waves", "braid", "earrings");
			case LUCIA -> Set.of("side_part", "bob", "earrings", "bag");
			case MBRIANA -> Set.of("long_hair", "scarf", "hairband");
			case PARTENOPE -> Set.of("long_hair", "ponytail");
			case ANIMA -> Set.of("hood");
			case VENDOR -> Set.of("toque");
			case TAILOR, ARMORER -> Set.of("coppola");
			case MOTOR_DEALER -> Set.of();
			case BUSKER -> Set.of("curls", "mandolin");
			case PORTER -> Set.of("headscarf", "earrings", "skirt");
			case ADA -> Set.of("long_hair", "hairband", "earrings");
			case MICHELE -> Set.of("coppola");
			case NICO -> Set.of("hood", "scarf");
			case SALVATORE -> Set.of("beret");
			case ROSA -> Set.of("bun", "earrings", "skirt");
			case ANTONIO -> Set.of("bandana", "scarf");
			case CARABINIERE -> Set.of("cc_cap");
			case CARMELA -> Set.of("long_hair", "hairband", "earrings");
			case GIUSY -> Set.of("waves", "long_hair", "earrings", "skirt");
			case SOFIA -> Set.of("side_part", "long_hair", "bag");
			case GIORGIA_MELONI -> Set.of("side_part", "bob", "long_hair");
			case PEPPE_FETISH -> Set.of("hood");
			case NONNA -> Set.of("headscarf", "skirt", "bag");
			case PARCHEGGIATORE -> Set.of("cap");
			case TIFOSO -> Set.of("curls", "scarf");
			case SCUGNIZZO -> Set.of("cap", "fringe");
			case PRESEPAIO -> Set.of("coppola");
			case PIZZAIOLO -> Set.of("toque");
			case BUCATO -> Set.of("headscarf", "skirt");
			case TURISTA -> Set.of("cap", "bag");
			case PESCATORE -> Set.of("beret", "scarf");
			case CUSTODE_TUNNEL -> Set.of("hood", "scarf");
			case CARTOMANTE -> Set.of("long_hair", "hairband", "earrings", "skirt");
			case MERCATANTE -> Set.of("coppola");
			case MUSICISTA -> Set.of("side_part", "mandolin");
			case GUARDIANO_PRESEPE -> Set.of("coppola", "scarf");
			case PASSERBY -> switch (variant) {
				case 0 -> Set.of("coppola");                    // 'o nonno
				case 1 -> Set.of("bun", "skirt", "earrings");    // 'a signora
				case 2 -> Set.of("cap");                         // 'o guaglione
				case 3 -> Set.of("long_hair", "hairband", "skirt"); // 'a guagliona
				case 4 -> Set.of("curls");                       // 'o tifoso
				default -> Set.of("headscarf", "skirt");         // 'a nonna
			};
		};
	}

	static boolean female(Role role, int variant) {
		return role.female() || (role == Role.PASSERBY && (variant == 1 || variant == 3 || variant == 5));
	}

	@Override
	public void setupAnim(State state) {
		super.setupAnim(state);
		boolean peppe = state.role == Role.MOTOR_DEALER;
		head.skipDraw = peppe;
		hat.visible = !peppe;
		peppePortrait.visible = peppe;
		Set<String> on = parts(state.role, state.variant);
		curls.visible = on.contains("curls"); ponytail.visible = on.contains("ponytail"); cap.visible = on.contains("cap");
		scarf.visible = on.contains("scarf"); fringe.visible = on.contains("fringe"); bob.visible = on.contains("bob");
		braid.visible = on.contains("braid"); curlsTop.visible = on.contains("curls_top"); earrings.visible = on.contains("earrings");
		hood.visible = on.contains("hood"); bag.visible = on.contains("bag"); waves.visible = on.contains("waves");
		sidePart.visible = on.contains("side_part"); toque.visible = on.contains("toque"); coppola.visible = on.contains("coppola");
		mandolin.visible = on.contains("mandolin"); headscarf.visible = on.contains("headscarf"); longHair.visible = on.contains("long_hair");
		bun.visible = on.contains("bun"); hairband.visible = on.contains("hairband"); skirt.visible = on.contains("skirt");
		ccCap.visible = on.contains("cc_cap"); beret.visible = on.contains("beret"); bandana.visible = on.contains("bandana");
		// girls: slimmer arms (scaled around the shoulder, they stay attached to the body)
		float arm = female(state.role, state.variant) ? 0.75f : 1f;
		rightArm.xScale = arm; leftArm.xScale = arm; rightArm.zScale = arm == 1f ? 1f : 0.9f; leftArm.zScale = rightArm.zScale;
		if (state.isPassenger) {
			rightLeg.xRot = -0.99f; leftLeg.xRot = -0.99f;
			rightLeg.yRot = 0.23f; leftLeg.yRot = -0.23f;
			rightLeg.zRot = 0.18f; leftLeg.zRot = -0.18f;
			if (state.role == Role.GENNARO) {
				body.xRot = 0.13f;
				leftArm.xRot = -0.97f; rightArm.xRot = -0.97f;
				leftArm.zRot = -0.12f; rightArm.zRot = 0.12f;
			} else {
				leftArm.xRot = -0.50f; rightArm.xRot = -0.50f;
			}
		}
	}

	static final class State extends HumanoidRenderState {
		Role role = Role.GENNARO;
		int variant;
	}

	@SuppressWarnings("unused") private static NapoliResident unused;
}
