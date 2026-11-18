package org.embeddedt.embeddium.impl.render.chunk.sprite;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the item-425 take of upstream {@code d94ec1e59} ("Avoid carrying the transparency level enum
 * through pixel loops", JDK-8379667): the sprite pixel loop in {@code TextureAtlasSpriteMixin} no longer
 * carries a {@link SpriteTransparencyLevel} through the loop; it accumulates two booleans and folds them
 * back with {@link SpriteTransparencyLevel#of(boolean, boolean)} plus one {@code chooseNextLevel} at the
 * end. That rewrite is only safe if the folded result is identical to the old enum-carrying loop for every
 * possible pixel sequence, which is what this test proves exhaustively over the alpha classes the loop
 * distinguishes (alpha == 0, 0 &lt; alpha &lt; 255, alpha == 255).
 *
 * <p>The old loop is transcribed here as the reference ({@link #oldLoop}); the new computation is
 * transcribed as {@link #newLoop} and must agree with it for every previous level and every sequence.
 * The mixin itself lives in the forge122 module, so this test pins the common-module contract
 * ({@code of} + {@code chooseNextLevel}) that the mixin's new loop is built from; the mixin's loop body
 * is the verbatim upstream change.
 */
class SpriteTransparencyLevelEquivalenceTest {

    /** Alpha representatives: one per class the loop branches on, plus near-boundary values. */
    private static final int[] ALPHA_CLASSES = {0, 1, 128, 254, 255};

    /** The pre-change loop, transcribed from TextureAtlasSpriteMixin before the take. */
    private static SpriteTransparencyLevel oldLoop(SpriteTransparencyLevel prevLevel, int[] alphas) {
        SpriteTransparencyLevel level = prevLevel;
        for (int alpha : alphas) {
            if (alpha > 0) {
                if (alpha < 255) {
                    level = level.chooseNextLevel(SpriteTransparencyLevel.TRANSLUCENT);
                } else {
                    level = level.chooseNextLevel(SpriteTransparencyLevel.OPAQUE);
                }
            } else {
                level = level.chooseNextLevel(SpriteTransparencyLevel.TRANSPARENT);
            }
        }
        return level;
    }

    /** The post-change computation, transcribed from the mixin's new loop body. */
    private static SpriteTransparencyLevel newLoop(SpriteTransparencyLevel prevLevel, int[] alphas) {
        boolean hasTransparent = false;
        boolean hasTranslucent = false;
        for (int alpha : alphas) {
            if (alpha > 0) {
                if (alpha < 255) {
                    hasTranslucent = true;
                }
            } else {
                hasTransparent = true;
            }
        }
        return prevLevel.chooseNextLevel(SpriteTransparencyLevel.of(hasTransparent, hasTranslucent));
    }

    @Test
    void ofTruthTable() {
        assertEquals(SpriteTransparencyLevel.OPAQUE, SpriteTransparencyLevel.of(false, false));
        assertEquals(SpriteTransparencyLevel.TRANSPARENT, SpriteTransparencyLevel.of(true, false));
        assertEquals(SpriteTransparencyLevel.TRANSLUCENT, SpriteTransparencyLevel.of(false, true));
        assertEquals(SpriteTransparencyLevel.TRANSLUCENT, SpriteTransparencyLevel.of(true, true));
    }

    @Test
    void exhaustiveOverAlphaClassesUpToLength4() {
        for (SpriteTransparencyLevel prev : SpriteTransparencyLevel.values()) {
            check(prev, new int[0]);
            for (int a : ALPHA_CLASSES) {
                check(prev, new int[]{a});
                for (int b : ALPHA_CLASSES) {
                    check(prev, new int[]{a, b});
                    for (int c : ALPHA_CLASSES) {
                        check(prev, new int[]{a, b, c});
                        for (int d : ALPHA_CLASSES) {
                            check(prev, new int[]{a, b, c, d});
                        }
                    }
                }
            }
        }
    }

    @Test
    void randomLongSequences() {
        Random random = new Random(0x425L);
        for (int trial = 0; trial < 200; trial++) {
            int[] alphas = new int[256];
            for (int i = 0; i < alphas.length; i++) {
                alphas[i] = random.nextInt(256);
            }
            for (SpriteTransparencyLevel prev : SpriteTransparencyLevel.values()) {
                check(prev, alphas);
            }
        }
    }

    private static void check(SpriteTransparencyLevel prev, int[] alphas) {
        assertEquals(oldLoop(prev, alphas), newLoop(prev, alphas),
                "prev=" + prev + " seq=" + java.util.Arrays.toString(alphas));
    }
}
