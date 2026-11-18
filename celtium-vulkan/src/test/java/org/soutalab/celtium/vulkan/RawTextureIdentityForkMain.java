package org.soutalab.celtium.vulkan;

/**
 * The switch-on arm of the raw-texture-identity contract (item 365, audit section 6 M-B), run in a
 * forked JVM: {@link RenderTargetRegistry#RAW_TEXTURE_IDENTITY} is a static final read at class
 * initialisation, so the property {@code -Dceltium.vulkan.rawFboTextureIdentity=true} must be set
 * before the class loads, which the in-process JUnit JVM cannot do after the fact.
 * {@link RawTextureIdentityContractTest} forks this main with the property set and requires the
 * PASS line plus exit code 0.
 * <p>
 * The scenario is Xaero's atlas rollover as M-A established it statically: one framebuffer whose
 * colour attachment is swapped between two atlas textures. Under the switch each texture owns its
 * own identity, so binding the second texture must not evict the first texture's reverse lookup -
 * the exact loss the default mode documents (and the contract test pins) for the same sequence.
 */
final class RawTextureIdentityForkMain {

    static final int FRAMEBUFFER = 71;
    static final int ATLAS_A = 301;
    static final int ATLAS_B = 302;
    static final int EXTENT = 1024;

    private RawTextureIdentityForkMain() {
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (AssertionError failure) {
            System.out.println("CELTIUM_RAW_TEXTURE_IDENTITY_FORK FAIL: " + failure.getMessage());
            System.exit(1);
        }
        System.out.println("CELTIUM_RAW_TEXTURE_IDENTITY_FORK PASS");
    }

    static void run() {
        check(RenderTargetRegistry.RAW_TEXTURE_IDENTITY,
                "fork must run with -Dceltium.vulkan.rawFboTextureIdentity=true");
        long identityA = RenderTargetRegistry.rawTargetIdentity(FRAMEBUFFER, ATLAS_A);
        long identityB = RenderTargetRegistry.rawTargetIdentity(FRAMEBUFFER, ATLAS_B);
        check(identityA != identityB, "switch on: the two atlas textures own distinct identities");
        check(identityA == RenderTargetRegistry.rawColorTextureIdentity(ATLAS_A),
                "switch on: identity is keyed by texture A");
        check(identityB == RenderTargetRegistry.rawColorTextureIdentity(ATLAS_B),
                "switch on: identity is keyed by texture B");
        check(identityA != RenderTargetRegistry.rawFramebufferIdentity(FRAMEBUFFER),
                "switch on: texture identity is not the framebuffer identity");

        PublishedFrameTextures published = new PublishedFrameTextures();
        published.bindColorTexture(identityA, ATLAS_A, EXTENT, EXTENT);
        published.bindColorTexture(identityB, ATLAS_B, EXTENT, EXTENT);
        check(published.identityForColorTexture(ATLAS_A) == identityA,
                "switch on: atlas A stays resolvable after B is bound");
        check(published.identityForColorTexture(ATLAS_B) == identityB,
                "switch on: atlas B resolves to its own identity");
        check(published.colorTextureName(identityA) == ATLAS_A,
                "switch on: identity A still names atlas A");
        check(published.colorTextureName(identityB) == ATLAS_B,
                "switch on: identity B names atlas B");

        // Rollover back to the first atlas (the framebuffer is re-pointed at A): B must survive too,
        // because a sampled name from the older atlas is exactly what the default mode loses.
        published.bindColorTexture(identityA, ATLAS_A, EXTENT, EXTENT);
        check(published.identityForColorTexture(ATLAS_B) == identityB,
                "switch on: atlas B stays resolvable after rollover back to A");
    }
}
