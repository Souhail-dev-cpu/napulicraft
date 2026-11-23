import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Paints every city person (128x128, UVs as in NapoliResidentModel). Each look has its own face, clothes and props,
 * so no two roles share the same outfit. Run from D:\NapuliMod: java tools/GenResidentTextures.java
 */
public class GenResidentTextures {
	record Look(String name, boolean female, int skin, int hair, int eye, int top, int trim, int legs, int shoes, int accent, Consumer<BufferedImage> extra) {}

	static final int GOLD = 0xe0b84a, WHITE = 0xf1efe8, NAPOLI = 0x4fa3df;

	public static void main(String[] args) throws Exception {
		Look[] looks = {
			look("gennaro", false, 0xbb835f, 0x183950, 0x314747, NAPOLI, WHITE, 0x253b54, 0xe2e9e5, 0x183950, i -> { stripesArm(i, WHITE); number10(i, WHITE); }),
			look("ciro", false, 0x9c6049, 0x271a17, 0x453524, 0x282b35, 0xffe0a3, 0x384a32, 0x26252a, 0x282b35, i -> { strings(i, 0xffe0a3); beard(i, 0x271a17); }),
			look("enzo", false, 0xd8a579, 0x5d3929, 0x4a3a2d, 0x99362f, 0xe5ddc9, 0x223b51, 0xd1d4c9, 0x5d3929, i -> sash(i, 0xe3c9ac)),
			look("teresa", true, 0xd49878, 0x4b2b21, 0x647245, 0x815273, 0xe4b992, 0x2d3748, 0x4d3940, GOLD, i -> apron(i, 0xe6ddc9, 25)),
			look("lucia", true, 0xc28662, 0x38261f, 0x386450, 0x2e7465, 0xe5c99b, 0x414d64, 0x342921, 0x793f30, i -> hStripes(i, 0x407c68)),
			look("mbriana", true, 0xc7d9d0, 0x94b1a3, 0x679391, 0xe0e3cf, 0xbab9e4, 0xa4b9ac, 0x8c9d9c, 0xbab9e4, i -> {}),
			look("partenope", true, 0x86b9b0, 0x17495f, 0xb7fafa, 0x286c78, 0xe9d3a1, 0x24546b, 0x235064, 0xe9d3a1, i -> scales(i)),
			look("anima", false, 0xc7c8b6, 0x626d66, 0xf2ffff, 0x384643, 0x8cbab0, 0x303a38, 0x313334, 0x384643, i -> hollowEyes(i)),
			look("vendor", false, 0xbb8568, 0x46352c, 0x3a2823, WHITE, 0xc0392b, 0xece6da, 0x493934, WHITE, i -> { neckerchief(i, 0xc0392b); apron(i, 0xfafafa, 24); mustache(i, 0x2a1d17); flour(i); }),
			look("tailor", false, 0xd1a487, 0x6d6d6d, 0x364c34, 0x5b4632, 0xf2efe6, 0x55575c, 0x352d30, 0x6e6352, i -> { waistcoat(i, 0x5b4632, 0xf2efe6); tape(i); }),
			look("busker", false, 0xb17956, 0x332329, 0x4b3328, 0x22314f, WHITE, 0x343e4c, 0x554634, 0x8a5a2e, i -> { hStripes(i, WHITE); stubble(i, 0x332329); }),
			look("porter", true, 0x9b644a, 0x302723, 0x392922, 0x8f8f8f, 0x2b5c8a, 0x2b2f38, 0x1e2a33, 0xc0392b, i -> { apron(i, 0x2b5c8a, 23); dots(i, 90, 64, 126, 78, WHITE); }),
			look("passerby_0", false, 0xc9916e, 0xb9b9b4, 0x3b3b3b, 0xc9b48a, 0x8a7a55, 0x5b4632, 0x2b2420, 0x6e6352, i -> { cardigan(i); mustache(i, 0x9a9a94); }),
			look("passerby_1", true, 0xc58e65, 0x6f5546, 0x42382d, 0xd9708e, WHITE, 0xd9708e, 0x3a2a2a, 0xd9708e, i -> { dots(i, 20, 20, 28, 32, WHITE); dots(i, 74, 90, 103, 100, WHITE); }),
			look("passerby_2", false, 0xb07a58, 0x1f1a17, 0x3a2a20, NAPOLI, WHITE, NAPOLI, 0xf2f2f2, 0xf4f4f4, i -> { stripesArm(i, WHITE); legStripe(i, WHITE); }),
			look("passerby_3", true, 0xe0b090, 0xc9a24a, 0x3d6b8a, WHITE, 0xe86a9a, 0x3d5f8f, 0xf2f2f2, 0xe86a9a, i -> {}),
			look("passerby_4", false, 0xa8704f, 0x161210, 0x2e2018, NAPOLI, WHITE, WHITE, 0x1f1f1f, 0x161210, i -> { number10(i, WHITE); stubble(i, 0x161210); }),
			look("passerby_5", true, 0xc9a084, 0x1c1c1c, 0x3a3a3a, 0x1f1f22, 0x2e2e33, 0x1f1f22, 0x111111, 0x1c1c1c, i -> {}),
			look("ada", true, 0xc58d6c, 0x4e3224, 0x4a3829, 0x263f77, 0xf0c478, 0x2e303a, 0x292828, 0xf0c478, i -> goldTrim(i)),
			look("michele", false, 0xdbad88, 0x43302a, 0x424934, 0x1f3550, 0xedcb8b, 0x444a51, 0x222835, 0x4b4f55, i -> { buttons(i, GOLD); mustache(i, 0x43302a); }),
			look("nico", false, 0xa9755c, 0x2e231e, 0x3e3226, 0x274d89, 0xf0c478, 0x344e66, 0x252628, 0x274d89, i -> { chain(i); stubble(i, 0x2e231e); }),
			look("salvatore", false, 0xb98465, 0x292522, 0x424023, 0x2a6a63, 0xd0ead7, 0x313e43, 0x2b2824, 0x1d2a44, i -> { hStripes(i, 0xd0ead7); beard(i, 0x292522); }),
			look("rosa", true, 0xc38363, 0x603a31, 0x3e513e, 0x338f85, 0xe8efe2, 0x3b443d, 0x46362c, 0x338f85, i -> apron(i, 0xe8efe2, 26)),
			look("antonio", false, 0xac7355, 0x382920, 0x554132, 0x205d54, 0xd0ead7, 0x46474c, 0x252b2a, 0xc0392b, i -> { tank(i); dots(i, 80, 104, 116, 115, WHITE); scar(i); }),
			look("carabiniere_0", false, 0xc79a78, 0x2a211c, 0x3a2a20, 0x1a2233, 0xc0c4c8, 0x1a2233, 0x0e0e10, 0x1a2233, i -> carabiniere(i, false)),
			look("carabiniere_1", false, 0xa8785a, 0x1c1714, 0x2a2018, 0x1a2233, 0xc0c4c8, 0x1a2233, 0x0e0e10, 0x1a2233, i -> carabiniere(i, true)),
			look("carmela", true, 0xd2a07e, 0x24180f, 0x5a3a20, 0xb3262a, WHITE, 0x2b2b30, 0x2b2420, WHITE, i -> { apron(i, 0xfafafa, 24); flour(i); }),
			look("giusy", true, 0xc68a62, 0x6b4a2e, 0x3d2a1c, 0xd1a467, 0x2a1d14, 0x141414, 0x141414, 0x141414, i -> { leopard(i); highlights(i, 0xd9b26a); }),
			look("sofia", true, 0xe2b694, 0x3b2a20, 0x4b6b3a, 0x4a6fa5, WHITE, 0x2f3e57, 0xe8e8e8, 0x8a5a2e, i -> { tee(i); glasses(i); }),
			// v8: the people of the centro storico
			look("nonna", true, 0xc9a084, 0xc8c8c2, 0x3a3a3a, 0x262628, 0x6e6352, 0x262628, 0x111111, 0x6e6352, i -> { apron(i, 0x7a8a9a, 24); dots(i, 20, 25, 28, 32, 0xd9708e); }),
			look("parcheggiatore", false, 0xb07a58, 0x1f1a17, 0x3a2a20, 0xd4e157, 0xff9800, 0x3d5f8f, 0x2b2420, 0xd4e157, i -> { sash(i, 0xd8dde0); stubble(i, 0x1f1a17); }),
			look("tifoso", false, 0xc58e65, 0x2a1d14, 0x3a2a20, NAPOLI, WHITE, NAPOLI, 0xf2f2f2, WHITE, i -> { number10(i, WHITE); stripesArm(i, WHITE); stubble(i, 0x2a1d14); }),
			look("scugnizzo", false, 0xd1a07a, 0x2b1d14, 0x4a3a2d, 0xe53935, WHITE, 0x3d5f8f, 0xf2f2f2, 0xe53935, i -> tee(i)),
			look("presepaio", false, 0xd1a487, 0x9a9a94, 0x364c34, 0x5d4037, 0xf2efe6, 0x55575c, 0x352d30, 0x8d6e63, i -> { apron(i, 0xd7ccc8, 23); glasses(i); mustache(i, 0x9a9a94); }),
			look("pizzaiolo", false, 0xc79a78, 0x2a211c, 0x3a2a20, WHITE, 0xc0392b, 0x2b2b30, 0x1e1e1e, WHITE, i -> { neckerchief(i, 0xc0392b); apron(i, 0xfafafa, 24); flour(i); mustache(i, 0x2a211c); }),
			look("giorgia_meloni", true, 0xe0b496, 0xb28a50, 0x587a91, 0x202127, WHITE, 0x24252b, 0x121318, 0xb28a50, GenResidentTextures::meloni),
			look("motor_dealer", false, 0xb07c55, 0xb45e2a, 0x4d5340, 0x69717a, 0x4b7ca9, 0x324f74, 0xe6e6de, 0x69717a, GenResidentTextures::peppe),
			look("bucato", true, 0xb98868, 0x6d4936, 0x566142, 0x578f89, WHITE, 0x3e5a70, 0x4c3c35, 0xc4dfdb, i -> apron(i, 0xf0e4ce, 24)),
			look("turista", false, 0xd6a786, 0x6a4430, 0x496b79, 0xdeb251, 0xffe8ad, 0x476774, 0xdeceab, 0x26788d, i -> hStripes(i, 0xf4d182)),
			look("pescatore", false, 0x9c7055, 0x50463c, 0x536353, 0x143b56, 0xf0c35b, 0x384c59, 0x272b30, 0xe3a93c, i -> { hStripes(i, 0x285e70); beard(i, 0x50463c); }),
			look("custode_tunnel", false, 0xbe947b, 0x424344, 0x647570, 0x4b5148, 0xb8b37c, 0x373e39, 0x383038, 0x7a7655, i -> { sash(i, 0xc2a363); beard(i, 0x424344); }),
			look("cartomante", true, 0xc9906f, 0x342739, 0x643e66, 0x58265e, GOLD, 0x352440, 0x292235, 0xba955b, i -> sash(i, 0xa35782)),
			look("mercatante", false, 0xb57953, 0x302620, 0x363c29, 0xa04436, 0xf5d8ac, 0x545746, 0x332d2d, 0x7c5233, i -> { apron(i, 0xe4bda2, 24); mustache(i, 0x302620); }),
			look("musicista", false, 0xc19b80, 0x30252a, 0x4b5f72, 0x252c4b, WHITE, 0x26253c, 0x1e1b29, 0xb04358, i -> neckerchief(i, 0xb04358)),
			look("guardiano_presepe", false, 0xd4a582, 0x999087, 0x515c40, 0x385749, 0xb24737, 0x504542, 0x392e30, 0x693a39, i -> { glasses(i); mustache(i, 0x999087); apron(i, 0xac8161, 23); }),
			look("peppe_fetish", false, 0xdfb49e, 0x302925, 0x503328, 0x343c3c, WHITE, 0x373a39, 0x292827, 0x343c3c, GenResidentTextures::motorDealer)
		};
		for (Look l : looks) {
			if (args.length > 0 && !java.util.Arrays.asList(args).contains(l.name)) continue;
			BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
			base(img, l);
			l.extra().accept(img);
			if (l.name.startsWith("carabiniere")) bandolier(img);
			ImageIO.write(img, "PNG", Path.of("src/main/resources/assets/napuli/textures/entity/" + l.name + ".png").toFile());
		}
		System.out.println("residents painted: " + looks.length);
	}

	static Look look(String n, boolean f, int skin, int hair, int eye, int top, int trim, int legs, int shoes, int accent, Consumer<BufferedImage> extra) {
		return new Look(n, f, skin, hair, eye, top, trim, legs, shoes, accent, extra);
	}

	// ------------------------------------------------------------ the common body
	static void base(BufferedImage img, Look l) {
		cube(img, 0, 0, 8, 8, 8, l.skin);                 // head
		cube(img, 16, 16, 8, 12, 4, l.top);               // body
		cube(img, 40, 16, 4, 12, 4, l.top);               // arms (both use these UVs)
		cube(img, 0, 16, 4, 12, 4, l.legs);               // legs
		// hands and shoes
		rect(img, 40, 29, 56, 32, l.skin); rect(img, 44, 16, 48, 20, l.top); rect(img, 48, 16, 52, 20, l.skin);
		rect(img, 0, 30, 16, 32, l.shoes); rect(img, 8, 16, 12, 20, l.shoes);
		// collar and belt
		rect(img, 22, 20, 26, 21, shade(l.top, 0.75));
		if (!l.female) rect(img, 16, 28, 40, 29, shade(l.legs, 0.6));
		// hair on the head: top, back, a hairline on the sides
		rect(img, 8, 0, 16, 8, l.hair);
		rect(img, 24, 8, 32, l.female ? 16 : 12, l.hair);     // back of the head
		rect(img, 0, 8, 8, l.female ? 14 : 10, l.hair);       // right side
		rect(img, 16, 8, 24, l.female ? 14 : 10, l.hair);     // left side
		rect(img, 0, 10, 2, 12, l.female ? l.hair : shade(l.skin, 0.9)); // ear area / sideburns
		face(img, l);
		// every hair/hat part in the hair colour; scarf/trim parts in the trim colour
		rect(img, 0, 34, 34, 64, l.hair);
		rect(img, 35, 34, 63, 53, l.trim);
		rect(img, 64, 0, 105, 39, l.hair);
		rect(img, 84, 24, 89, 27, GOLD);                        // earrings
		rect(img, 86, 31, 110, 38, shade(l.top, 0.75));         // hood
		rect(img, 86, 40, 112, 48, 0x793f30); rect(img, 100, 40, 112, 44, 0xd6ae72); // bag
		rect(img, 64, 48, 126, 62, l.hair);
		for (int x = 65; x < 126; x += 4) rect(img, x, 48, x + 1, 60, shade(l.hair, 1.21));
		// new parts
		cubeShaded(img, 0, 64, 7, 6, 7, WHITE);                 // toque
		cubeShaded(img, 0, 78, 8, 1, 8, 0xdedad0);
		tweed(img, 36, 64, 72, 75, 0x6e6352);                    // coppola
		rect(img, 36, 76, 57, 80, 0x4d4538);
		rect(img, 74, 64, 87, 73, 0x9b6a35); rect(img, 79, 67, 81, 69, 0x2a1a0e); rect(img, 74, 74, 78, 81, 0x3b2616); // mandolin
		rect(img, 90, 64, 126, 78, l.accent);                    // headscarf
		rect(img, 0, 90, 21, 102, l.hair);                       // long hair
		for (int x = 1; x < 21; x += 3) rect(img, x, 90, x + 1, 102, shade(l.hair, 0.82));
		rect(img, 22, 90, 34, 96, l.hair);                       // bun
		rect(img, 36, 90, 72, 100, l.accent);                    // hairband
		rect(img, 74, 90, 103, 100, l.female ? l.accent : l.legs); // skirt
		rect(img, 74, 99, 103, 100, shade(l.accent, 0.7));
		rect(img, 40, 104, 77, 115, 0x1d2a44);                   // beret
		rect(img, 80, 104, 116, 115, 0xb52a24);                  // bandana
		ccCap(img);
	}

	/** face front is x 8..15, y 8..15 */
	static void face(BufferedImage img, Look l) {
		rect(img, 8, 8, 16, 9, l.hair);                                    // hairline
		if (l.female) { rect(img, 8, 9, 9, 13, l.hair); rect(img, 15, 9, 16, 13, l.hair); } // hair framing the face
		int brow = shade(l.hair, 0.8);
		rect(img, 9, 10, 11, 11, brow); rect(img, 13, 10, 15, 11, brow);  // eyebrows
		rect(img, 9, 11, 10, 12, 0xf4efe6); rect(img, 10, 11, 11, 12, l.eye); // eyes: white + iris, looking forward
		rect(img, 13, 11, 14, 12, l.eye); rect(img, 14, 11, 15, 12, 0xf4efe6);
		rect(img, 11, 12, 13, 13, shade(l.skin, 0.86));                    // nose shadow
		if (l.female) {
			px(img, 9, 10, 0x1e1410); px(img, 14, 10, 0x1e1410);           // lashes
			px(img, 9, 12, mix(l.skin, 0xe08a8a, 0.35)); px(img, 14, 12, mix(l.skin, 0xe08a8a, 0.35)); // blush
			rect(img, 11, 13, 13, 14, 0xb5434e);                            // lips
			px(img, 11, 14, shade(l.skin, 0.92)); px(img, 12, 14, shade(l.skin, 0.92));
		} else {
			rect(img, 10, 13, 14, 14, shade(l.skin, 0.72));                // mouth
		}
		rect(img, 8, 15, 16, 16, shade(l.skin, 0.93));                     // chin shade
	}

	// ------------------------------------------------------------ outfit details
	static void apron(BufferedImage i, int c, int fromY) { rect(i, 20, fromY, 28, 32, c); rect(i, 21, 20, 22, fromY, c); rect(i, 26, 20, 27, fromY, c); }
	static void neckerchief(BufferedImage i, int c) { rect(i, 21, 20, 27, 22, c); rect(i, 23, 22, 25, 24, c); }
	static void flour(BufferedImage i) { px(i, 22, 28, 0xffffff); px(i, 25, 26, 0xffffff); px(i, 45, 24, 0xffffff); px(i, 10, 4, 0xffffff); }
	static void mustache(BufferedImage i, int c) { rect(i, 10, 13, 14, 14, c); px(i, 9, 13, c); px(i, 14, 13, c); }
	static void beard(BufferedImage i, int c) { rect(i, 8, 13, 16, 16, shade(c, 1.1)); rect(i, 10, 13, 14, 14, shade(c, 0.9)); px(i, 11, 14, 0xb06a5a); px(i, 12, 14, 0xb06a5a); }
	static void stubble(BufferedImage i, int c) { for (int x = 8; x < 16; x += 2) px(i, x, 15, mix(c, 0x888888, 0.3)); px(i, 9, 14, mix(c, 0x777777, 0.4)); px(i, 14, 14, mix(c, 0x777777, 0.4)); }
	static void scar(BufferedImage i) { px(i, 13, 10, 0xd9a28a); px(i, 14, 12, 0xd9a28a); }
	static void strings(BufferedImage i, int c) { rect(i, 22, 21, 23, 25, c); rect(i, 25, 21, 26, 25, c); rect(i, 21, 27, 27, 30, shade(0x282b35, 0.75)); }
	static void sash(BufferedImage i, int c) { for (int d = 0; d < 8; d++) rect(i, 20 + d, 21 + d / 2, 21 + d, 23 + d / 2, c); }
	static void hStripes(BufferedImage i, int c) { for (int y = 21; y < 32; y += 2) { rect(i, 16, y, 40, y + 1, c); rect(i, 40, y, 56, y + 1, c); } }
	static void stripesArm(BufferedImage i, int c) { rect(i, 40, 20, 41, 29, c); rect(i, 47, 20, 48, 29, c); rect(i, 16, 20, 17, 32, c); rect(i, 31, 20, 32, 32, c); }
	static void legStripe(BufferedImage i, int c) { rect(i, 0, 20, 1, 30, c); rect(i, 11, 20, 12, 30, c); }
	static void number10(BufferedImage i, int c) {
		// "10" on the back (32..40 x 20..32) and a small badge on the chest
		rect(i, 34, 22, 35, 28, c);
		rect(i, 36, 22, 39, 23, c); rect(i, 36, 27, 39, 28, c); rect(i, 36, 22, 37, 28, c); rect(i, 38, 22, 39, 28, c);
		rect(i, 25, 22, 27, 24, c);
	}
	static void waistcoat(BufferedImage i, int vest, int shirt) { rect(i, 20, 20, 28, 32, vest); rect(i, 23, 20, 25, 28, shirt); for (int y = 22; y < 31; y += 3) px(i, 23, y, GOLD); rect(i, 44, 20, 48, 29, shirt); }
	static void tape(BufferedImage i) { rect(i, 21, 20, 22, 27, 0xe8c547); rect(i, 26, 20, 27, 27, 0xe8c547); }
	static void cardigan(BufferedImage i) { rect(i, 23, 20, 25, 32, 0x8a7a55); for (int y = 22; y < 31; y += 3) px(i, 24, y, 0x5b4632); }
	static void goldTrim(BufferedImage i) { rect(i, 20, 31, 28, 32, GOLD); rect(i, 40, 28, 56, 29, GOLD); rect(i, 23, 20, 25, 24, GOLD); }
	static void buttons(BufferedImage i, int c) { for (int y = 21; y < 31; y += 3) { px(i, 22, y, c); px(i, 25, y, c); } }
	static void chain(BufferedImage i) { for (int x = 21; x < 27; x++) px(i, x, 22 + Math.abs(x - 23) / 2, GOLD); px(i, 23, 24, 0xf6dc7a); }
	static void tank(BufferedImage i) { rect(i, 40, 20, 56, 29, 0xac7355); rect(i, 20, 20, 21, 23, 0xac7355); rect(i, 27, 20, 28, 23, 0xac7355); }
	static void tee(BufferedImage i) { rect(i, 22, 20, 26, 32, WHITE); rect(i, 46, 20, 48, 29, shade(0x4a6fa5, 0.8)); }
	static void glasses(BufferedImage i) { rect(i, 8, 11, 16, 12, 0x2a2a2a); px(i, 9, 11, 0xdfe8ee); px(i, 10, 11, 0x4b6b3a); px(i, 13, 11, 0x4b6b3a); px(i, 14, 11, 0xdfe8ee); }
	static void scales(BufferedImage i) { for (int y = 20; y < 32; y += 2) for (int x = 16 + (y % 4); x < 40; x += 4) px(i, x, y, 0x5fb5b0); }
	static void hollowEyes(BufferedImage i) { rect(i, 9, 11, 11, 12, 0x101616); rect(i, 13, 11, 15, 12, 0x101616); px(i, 10, 11, 0xf2ffff); px(i, 13, 11, 0xf2ffff); }
	static void leopard(BufferedImage i) {
		// rosettes: a dark spot with a brown halo, on a staggered grid (not lines)
		for (int y = 21; y < 31; y += 3) for (int x = 17 + (y / 3 % 2) * 2; x < 55; x += 4) {
			px(i, x, y, 0x2a1d14);
			px(i, x + 1, y, 0x8a5a2e); px(i, x, y + 1, 0x8a5a2e);
		}
		rect(i, 22, 20, 26, 22, 0xc68a62); // neckline
	}
	static void highlights(BufferedImage i, int c) { for (int x = 64; x < 126; x += 3) rect(i, x, 48, x + 1, 62, c); for (int x = 1; x < 21; x += 3) rect(i, x, 90, x + 1, 102, c); px(i, 9, 8, c); px(i, 13, 8, c); }
	/** Motor dealer portrait and cardigan, separate from the fictional socks merchant. */
	static void motorDealer(BufferedImage i) {
		int skin = 0xe8bda7, hair = 0x352d29;
		// Face UV: fourteen columns, twenty rows. Features are placed from the front edge of that face.
		cubeShaded(i, 0, 64, 14, 20, 14, skin);
		for (int y = 0; y < 20; y++) for (int x = 0; x < 14; x++) {
			int color = x == 0 || x == 13 ? 0xc69581 : x == 1 || x == 12 ? 0xdbac95 : skin;
			if (y <= 5 && x > 2 && x < 11) color = 0xeec7ae;
			if (y >= 11 && (x == 2 || x == 11)) color = 0xd49c87;
			if (y >= 18 && (x < 3 || x > 10)) color = 0xc38e79;
			px(i, 14 + x, 78 + y, color);
		}
		for (int x : new int[] {14, 27}) rect(i, x, 78, x + 1, 83, hair);
		rect(i, 15, 78, 17, 79, hair);
		rect(i, 25, 78, 27, 79, hair);
		rect(i, 16, 84, 20, 85, 0x806052);
		rect(i, 22, 84, 26, 85, 0x806052);
		rect(i, 16, 85, 20, 88, 0xbd8a77);
		rect(i, 22, 85, 26, 88, 0xbd8a77);
		for (int x : new int[] {17, 23}) {
			rect(i, x, 86, x + 3, 87, 0xf2dcd0);
			px(i, x + 1, 86, 0x4f3026);
			px(i, x + 1, 87, 0x795044);
		}
		rect(i, 20, 88, 22, 92, 0xf0c8ad);
		px(i, 19, 91, 0xba836f); px(i, 22, 91, 0xba836f);
		rect(i, 18, 93, 24, 94, 0xb9675c);
		rect(i, 18, 94, 24, 96, 0x71362d);
		rect(i, 19, 94, 23, 95, 0xd7b5a2);
		rect(i, 19, 96, 23, 97, 0xc47766);
		cubeShaded(i, 58, 64, 3, 5, 2, 0xecc0a6);
		for (int y = 64; y < 85; y++) for (int x = 74; x < 112; x++) {
			int pattern = Math.floorMod(x * 7 + y * 11 + (x / 3) * (y / 2), 13);
			px(i, x, y, pattern < 3 ? 0x58473d : pattern < 8 ? hair : 0x282320);
		}
		for (int y = 20; y < 29; y++) for (int x = 16; x < 40; x++)
			px(i, x, y, (Math.floorMod(x * 3 + y * 7 + (x / 3) * (y / 2), 11) < 4) ? 0x68716c : 0x37413f);
		for (int y = 20; y < 29; y++) for (int x = 40; x < 56; x++)
			px(i, x, y, (Math.floorMod(x * 5 + y * 7, 13) < 4) ? 0x69726d : 0x384340);
		// White open shirt framed by the cardigan's dark ribbed edges.
		rect(i, 21, 20, 27, 22, 0xf6f0e7);
		rect(i, 22, 22, 26, 24, 0xf2eee8);
		rect(i, 23, 24, 25, 32, 0xeeeae1);
		rect(i, 20, 21, 21, 24, 0x242e2c); rect(i, 27, 21, 28, 24, 0x242e2c);
		rect(i, 22, 24, 23, 32, 0x28322f); rect(i, 25, 24, 26, 32, 0x28322f);
		px(i, 23, 20, 0xdcae96); px(i, 24, 20, 0xdcae96);
	}

	static void meloni(BufferedImage i) {
		rect(i, 22, 20, 26, 29, WHITE);
		rect(i, 23, 20, 25, 22, 0xe0b496); // open collar
		rect(i, 21, 21, 22, 25, 0x3c3e46); rect(i, 26, 21, 27, 25, 0x3c3e46); // jacket lapels
		px(i, 22, 25, 0x3c3e46); px(i, 25, 25, 0x3c3e46);
		rect(i, 22, 29, 26, 32, 0x202127); px(i, 24, 29, 0x929398); // jacket button
		rect(i, 44, 28, 48, 29, WHITE); // shirt cuffs
		rect(i, 20, 28, 22, 29, 0x111217); rect(i, 26, 28, 28, 29, 0x111217); // pockets
		highlights(i, 0xd7b879);
		for (int x = 65; x < 102; x += 3) rect(i, x, 5, x + 1, 20, 0xc6a361);
		rect(i, 8, 8, 11, 9, 0xd7b879); rect(i, 14, 8, 16, 10, 0xd7b879);
	}
	/** Original adult character in casual clothes: grey hoodie, blue T-shirt, jeans and trainers. */
	static void peppe(BufferedImage i) {
		beard(i, 0xa9532b);
		rect(i, 22, 20, 26, 32, 0x4b7ca9); // T-shirt beneath the open hoodie
		rect(i, 23, 20, 25, 21, 0xb07c55);
		rect(i, 21, 21, 22, 25, 0xe6e6de); rect(i, 26, 21, 27, 25, 0xe6e6de); // drawstrings
		rect(i, 20, 28, 22, 30, 0x4c535c); rect(i, 26, 28, 28, 30, 0x4c535c); // hoodie pockets
		rect(i, 4, 29, 8, 30, 0xe6e6de); rect(i, 5, 29, 7, 30, 0x929aa3); // trainers
	}
	static void dots(BufferedImage i, int x1, int y1, int x2, int y2, int c) { for (int y = y1; y < y2; y += 2) for (int x = x1 + (y % 4 == 0 ? 0 : 1); x < x2; x += 3) px(i, x, y, c); }

	/** Carabiniere: dark navy tunic, silver buttons, red stripe on the trousers, black boots */
	static void carabiniere(BufferedImage i, boolean old) {
		for (int y = 21; y < 31; y += 3) px(i, 24, y, 0xd6d9dc);
		rect(i, 20, 20, 28, 21, 0x10151f);
		rect(i, 0, 20, 1, 30, 0xc8102e); rect(i, 11, 20, 12, 30, 0xc8102e); rect(i, 8, 20, 9, 30, 0xc8102e); // the red "bande"
		rect(i, 0, 27, 16, 32, 0x0e0e10);                       // boots
		rect(i, 40, 28, 56, 29, 0xc0c4c8);                       // cuffs
		px(i, 21, 21, 0xc8102e); px(i, 26, 21, 0xc8102e);        // collar flames
		if (old) { mustache(i, 0x1c1714); rect(i, 9, 11, 15, 12, 0x15181c); px(i, 10, 11, 0x3a4a5a); px(i, 13, 11, 0x3a4a5a); } // sunglasses
	}
	/** the white bandolier across the chest and back */
	static void bandolier(BufferedImage i) {
		for (int d = 0; d < 12; d++) { int x = 20 + d * 8 / 12; px(i, x, 20 + d, 0xf5f5f5); px(i, x + 1, 20 + d, 0xe0e0e0); }
		for (int d = 0; d < 12; d++) { int x = 39 - d * 8 / 12; px(i, x, 20 + d, 0xf5f5f5); }
		rect(i, 25, 27, 27, 29, 0xd6d9dc);                       // buckle
	}
	static void ccCap(BufferedImage img) {
		rect(img, 0, 104, 36, 117, 0x141b2a);                    // crown: dark navy all round, top included
		rect(img, 0, 115, 36, 117, 0xc8102e);                    // red band along the bottom of the sides
		px(img, 13, 113, 0xd6d9dc); px(img, 13, 114, 0xb8bcc0); // small silver badge, front
		rect(img, 0, 117, 18, 120, 0x0b0b0d);                    // visor
		rect(img, 2, 119, 9, 120, 0x2a2a30);
	}

	// ------------------------------------------------------------ painting helpers
	static void cube(BufferedImage img, int x, int y, int w, int h, int d, int c) {
		rect(img, x + d, y, x + d + w, y + d, shade(c, 1.10));
		rect(img, x + d + w, y, x + d + 2 * w, y + d, shade(c, 0.66));
		rect(img, x, y + d, x + d, y + d + h, shade(c, 0.80));
		rect(img, x + d, y + d, x + d + w, y + d + h, c);
		rect(img, x + d + w, y + d, x + 2 * d + w, y + d + h, shade(c, 0.84));
		rect(img, x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h, shade(c, 0.94));
	}
	static void cubeShaded(BufferedImage img, int x, int y, int w, int h, int d, int c) {
		cube(img, x, y, w, h, d, c);
		rect(img, x + d, y + d + h - 1, x + 2 * d + 2 * w, y + d + h, shade(c, 0.85));
	}
	static void tweed(BufferedImage img, int x1, int y1, int x2, int y2, int c) {
		for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) img.setRGB(x, y, 0xff000000 | shade(c, ((x + y) % 3 == 0) ? 0.82 : ((x * 3 + y) % 5 == 0 ? 1.12 : 1)));
	}
	static int shade(int c, double f) {
		int r = Math.min(255, (int) (((c >> 16) & 255) * f)), g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
		return (r << 16) | (g << 8) | b;
	}
	static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}
	static void px(BufferedImage img, int x, int y, int c) { img.setRGB(x, y, 0xff000000 | c); }
	static void rect(BufferedImage img, int x1, int y1, int x2, int y2, int c) {
		for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) {
			double texture = (x * 13 + y * 29) % 7 == 0 ? 0.93 : 1;
			img.setRGB(x, y, 0xff000000 | shade(c, texture));
		}
	}
}
