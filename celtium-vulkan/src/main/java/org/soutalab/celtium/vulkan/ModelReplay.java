package org.soutalab.celtium.vulkan;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Native replay of compiled model display lists: the per-group mesh path and the instanced runs. Moved out of
 * {@link VulkanTerrainGpuBackend} unchanged; the state it reads stays there.
 */
final class ModelReplay {
    private final VulkanTerrainGpuBackend b;

    ModelReplay(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * Draws one captured display list natively, at the moment GL would have replayed it.
     * <p>
     * The recipe is the immediate path's own: the format-6 {@code POSITION_TEX} pipeline, the atlas and light map
     * descriptor, the combined matrix in the push constants, and a render pass opened for the duration because no pass
     * is open at {@code callList} time (measured: passOpen 0, passClosed 2003). The display list's vertices were captured
     * while {@code ModelRenderer} compiled it, and a display list is replayed under the current matrix rather than the
     * one it was compiled with - also measured - so the matrix used here is the one in effect now.
     * <p>
     * Lab switch: off, because this is a new draw path and the default must not change until a matched pair says the
     * image is the same. It was on for a while on the strength of terrain-only comparisons, which cannot see it: the
     * terrain-only scenes remove every entity before the capture. The entity scene then showed the difference - with the
     * draw on, the native path and the GL path differ on 18.49 % of the frame, and with only display list 2 taken
     * natively the difference is still 2.10 %, so the model geometry is not right yet. Keep it off until that is 0.
     */
    boolean celtium$drawModelList(int listId, int boundTextureId) {
        // A display list name is at least 1 (GL reserves 0 for "no list"), so a non-positive id here means the caller
        // asked for a model draw when no list was current. That is not a missing mesh and must not be counted as one:
        // it was logged as CELTIUM_MODEL_MISSING list=-1 with texture=107 on gv-model1, which reads as "this list failed
        // to capture" when the truth is "there was nothing to replay". Geometry drawn outside any list is immediate
        // geometry and belongs to the immediate path, which is where the caller should send it.
        if (listId <= 0) {
            b.modelNoListCalls++;
            return false;
        }
        if (VulkanDiagnostics.withinLimit(b.modelEntryLogs, 8)) {
            b.modelEntryLogs++;
            System.out.println("[Celtium] CELTIUM_MODEL_ENTRY list=" + listId + " texture=" + boundTextureId
                    + " modelDraw=" + b.MODEL_DRAW + " targetKnown=" + b.targetRegistry.targetKnown()
                    + " frame=" + b.frameId);
        }
        if (VulkanDiagnostics.enabled()) {
            // Phase B sizing: how many model draws share one (list, texture) in a frame, i.e. what an indirect multi-draw
            // would collapse. The collector reports on the frame change, so nothing new is printed from here.
            CeltiumModelBatchGroups.note(b.frameId, listId, boundTextureId);
        }
        // Which render target each model replay lands on. The entity-outline pass binds its own framebuffer, sets
        // renderOutlines and re-runs the same display lists through RenderLivingBase.renderModel
        // (`RenderGlobal.java:683-694`), so the mobs' geometry arrives here with a target that is not the main one, and
        // the group is stamped with it. A replay that is refused leaves the outline target empty and the glow invisible,
        // and no counter distinguishes "refused here" from "never called": this line does.
        long callTarget = b.targetRegistry.currentIdentity();
        boolean offTargetCall = callTarget != b.targetRegistry.mainIdentity();
        if (offTargetCall) {
            b.modelOffTargetCalls++;
            if (b.modelOffTargetLogged.add(listId + "@" + callTarget)
                    && b.modelOffTargetLogs < b.MODEL_OFFTARGET_LOG_LIMIT) {
                b.modelOffTargetLogs++;
                VulkanDynamicMesh looked = b.modelGeometry.modelMeshes() == null
                        ? null : b.modelGeometry.modelMeshes().get(Integer.valueOf(listId));
                System.out.println("[Celtium] CELTIUM_MODEL_OFFTARGET list=" + listId + " texture=" + boundTextureId
                        + " target=" + callTarget + " main=" + b.targetRegistry.mainIdentity()
                        + " modelDraw=" + b.MODEL_DRAW + " frameTargetKnown=" + b.targetRegistry.targetKnown()
                        + " meshes=" + (b.modelGeometry.modelMeshes() == null ? -1 : b.modelGeometry.modelMeshes().size())
                        + " inTable=" + (looked != null) + " verts=" + (looked == null ? -1 : looked.vertexCount()));
            }
            if ((b.modelOffTargetCalls == 1 || b.modelOffTargetCalls % 32 == 0) && VulkanDiagnostics.enabled()) {
                System.out.println("[Celtium] CELTIUM_MODEL_OFFTARGET_BATCHES calls=" + b.modelOffTargetCalls
                        + " drawn=" + b.modelOffTargetDraws
                        + " missingMeshes=" + b.modelGeometry.modelMissingMeshes()
                        + " refusals=" + b.modelDrawRefusals
                        + " lastRefusal=[" + b.modelLastRefusal + "]");
            }
        }
        if (!b.MODEL_DRAW || !b.targetRegistry.targetKnown()) {
            return false;
        }
        if (b.MODEL_ONLY_LIST != 0 && listId != b.MODEL_ONLY_LIST) {
            return false;
        }
        java.util.Map<Integer, VulkanDynamicMesh> meshes = b.modelGeometry.modelMeshes();
        VulkanDynamicMesh mesh = meshes == null ? null : meshes.get(Integer.valueOf(listId));
        if ((mesh == null || mesh.isEmpty()) && b.context != null
                && VulkanModelQuadCapture.quadsForList(listId) > 0
                && (b.modelMeshBuiltCaptureVersion != VulkanModelQuadCapture.captureVersion()
                    || b.modelGeometry.requestMissingCapturedListRebuild(
                            listId, VulkanModelQuadCapture.quadsForList(listId),
                            VulkanModelQuadCapture.captureVersion()))) {
            // The table is built at the frame's first terrain pass, and a model's own replay can come before the
            // next one: a list compiled in this frame is drawn for the first time in this frame, so the measured
            // shape was `CELTIUM_MODEL_MISSING` for every list's first replay while every later replay drew (leg
            // gv-cohort-dl11ent: 8 missing against 38538 draws). The geometry is already captured, so the build is
            // asked for it here - the same call the pass makes, which reuses every mesh whose generation has not
            // moved - and the lookup is retried once. A list nobody captured keeps the old path.
            // Normally once per capture version. If a captured list is absent from
            // the GPU table after a world switch, perform one bounded reconciliation
            // for that list and version rather than silently losing its first draws.
            b.celtium$buildModelMeshes();
            meshes = b.modelGeometry.modelMeshes();
            mesh = meshes == null ? null : meshes.get(Integer.valueOf(listId));
        }
        if (mesh == null || mesh.isEmpty()) {
            // Named rather than silent: a display list this renderer never captured is a piece of the scene that the GL
            // body would draw and that nothing draws without a context. Counting it is what turns "the model is missing"
            // into "list N, texture M, no mesh", which is the only form in which it can be fixed.
            b.modelGeometry.noteMissingMesh();
            if (b.modelGeometry.shouldLogMissingMesh(listId)) {
                System.out.println("[Celtium] CELTIUM_MODEL_MISSING list=" + listId + " texture=" + boundTextureId
                        + " capturedQuads=" + VulkanModelQuadCapture.quadsForList(listId)
                        + " captureVersion=" + VulkanModelQuadCapture.captureVersion()
                        + " cachedVersion=" + b.modelMeshBuiltCaptureVersion
                        + " meshBuildFailure=" + b.modelGeometry.modelMeshFailure()
                        + " (no GPU mesh available; GL-less cannot replay this list)");
            }
            return false;
        }
        // Declared outside the try because the finally below measures it: a local declared inside a try block is not
        // in scope in its finally.
        long modelEnqueueStart = b.BATCH_TIMINGS ? System.nanoTime() : 0L;
        try {
            // The texture the model is actually bound to, asked of GL at the moment of the replay: a player model samples
            // its own skin, not the block atlas the immediate path last used.
            int texture = boundTextureId != 0 ? boundTextureId : b.whiteTextureId();
            if (boundTextureId != 0 && b.textureRegistry != null
                    && b.textureRegistry.get(boundTextureId) == null) {
                b.modelDrawRefusals++;
                b.modelLastRefusal = "texture " + boundTextureId + " is not mirrored";
                return false;
            }
            int[] state = b.celtium$captureState();
            long key = b.celtium$stateKey(VulkanImmediateFormats.ITEM, state);
            // The layer passes: a display list replayed under a texture other than the one it was compiled with. The
            // spider's red eyes are one (LayerSpiderEyes rebinds the eyes texture and draws the same lists again with
            // additive blending and full-brightness light-map coordinates), and it is the one model family whose
            // measured ratio did not come to 1.0 after the lighting fix (0.99 r but 0.64-0.66 g/b). The body and the
            // layer draw the same geometry, so the difference between them can only be the state printed here: the
            // blend factors, the alpha test, the lighting flag and the resulting pipeline key.
            // The limit is asked first: once the lines are spent, this was still a synchronized map lookup, a string
            // and a set insertion on every layer replay.
            if (b.modelLayerLogs < b.MODEL_LAYER_LOG_LIMIT && boundTextureId != 0
                    && boundTextureId != org.soutalab.celtium.vulkan.VulkanModelQuadCapture.textureForList(listId)
                    && b.modelLayerLogged.add(listId + "|" + boundTextureId)) {
                b.modelLayerLogs++;
                System.out.println("[Celtium] CELTIUM_MODEL_LAYER list=" + listId
                        + " compileTexture=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.textureForList(listId)
                        + " replayTexture=" + boundTextureId
                        + " blend=" + state[0] + "(" + state[1] + "," + state[2] + ")"
                        + " depthTest=" + state[3] + " depthWrite=" + state[4] + " cull=" + state[5]
                        + " alphaRef=" + b.round3(Float.intBitsToFloat(state[6]))
                        + " depthFunc=" + state[7] + " lighting=" + state[8] + " key=" + key);
            }
            if (!b.modelStateLogged) {
                // What the model path is told about the state it draws under, once: the one-quad image showed the quads
                // over the terrain and the sky alike, and depth is the first thing that can do that.
                b.modelStateLogged = true;
                System.out.println("[Celtium] CELTIUM_MODEL_STATE list=" + listId + " depthTest=" + state[3]
                        + " depthWrite=" + state[4] + " cull=" + state[5] + " blend=" + state[0]
                        + " glDepthTest=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
                        + " glDepthMask=" + org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK)
                        + " key=" + key);
            }
            // Asked of GL here rather than taken from the immediate path's fields: a display list is replayed under the
            // matrix in effect at that moment, and the projection gets the same clip-depth remap the immediate path uses.
            // The mirror, not glGetFloatv. Asking GL here was measured to give the orthographic projection the HUD last
            // installed - "proj=0.0,0.0,-1.0,-0.05" for the model group against the terrain's perspective
            // "0,0,-1.001,-1" - which is exactly what the one-quad image showed: model quads laid out in screen space.
            // The immediate path's matrices come from this mirror for the same reason.
            // The mirror for both, which is the best measured pairing. Taking the model-view from GL instead was tested and
            // made the difference worse (6.35 % -> 18.30 % of the entity frame), so the live model-view is not the entity's
            // either - both sources report the camera there. That is consistent with what the capture records: a display
            // list stores its vertices already transformed, so the parts carry their own placement and the matrix must not
            // put them somewhere else. What the remaining difference is belongs to the capture's side of the pairing, not to
            // the live state.
            // Read in place: only the remap below and the log read it.
            float[] projectionNow = b.immediateState.peekMatrix(VulkanGlState.MATRIX_MODE_PROJECTION);
            // A list's stored vertices are model space, so the replay transform is the camera's change since the list
            // was captured: V_now * C_compile^-1 (ModelViewRelativizer, which short-circuits the identity the capture
            // currently reports for every list).
            // Identity was tried as an experiment - if the captured vertices carried their own placement, no model-view at all
            // would be right - and it measured worse than the mirror (8.08 % against 5.80 % of the entity frame), so the
            // camera rotation the mirror holds is needed. The mirror stays until the vertex space itself is settled.
            // Six pairings were measured: the mirror 5.80 % of the entity frame, identity 8.08, the captured compile-time
            // matrix 13.19, GL's live view 18.30, the mirror's rotation with GL's translation 18.40, GL's projection too
            // 18.49. No translation helping and a rotation helping says the vertices are already in the view space of the
            // moment they were captured in - so what has to be applied is the change of camera since then, which is exactly
            // V_now * C_compile^-1. Both halves are in hand: the mirror is V_now, the capture kept C_compile.
            // Scratch, as is the remapped projection: a queued group clones what it keeps (enqueue below).
            float[] modelViewNow = b.modelReplayView;
            System.arraycopy(b.immediateState.peekMatrix(VulkanGlState.MATRIX_MODE_MODELVIEW), 0, modelViewNow, 0, 16);
            // V_now * C_compile^-1, with the matrices and the inverse reused and the identity short-circuited: the
            // three Matrix4f temporaries and an inversion per replay were ~128 allocations a frame, and the capture
            // answers the identity for every list, so nearly all of that work composed the live view with itself.
            try {
                b.modelViewRelativizer.makeRelative(modelViewNow, listId);
            } catch (RuntimeException | LinkageError error) {
                b.modelLastRefusal = "relative view failed: " + error;
            }
            // The suspicion that the live projection had already been remapped was tested with a switch that copied it
            // through unchanged: the difference grew from 4.56 % to 6.01 % of the crop, so the remap is right and the
            // switch was removed rather than kept.
            float[] projectionRemapped = b.modelReplayProjection;
            b.remapClipDepth(projectionNow, projectionRemapped);
            // Gated and bounded: this line is per model list per frame, and on the operator's own session it was the
            // flood that pushed the game's log past 100,000 lines.
            if (VulkanDiagnostics.withinLimit(b.modelMatrixLines, b.MODEL_MATRIX_LINES)) {
                b.modelMatrixLines++;
                System.out.println("[Celtium] CELTIUM_MODEL_MATRIX list=" + listId
                        + " liveMVt=(" + b.round3(modelViewNow[12]) + "," + b.round3(modelViewNow[13]) + ","
                        + b.round3(modelViewNow[14]) + ")"
                        + " capturedMVt=(" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredMatrixTranslation()
                        + ")"
                        + " liveProj=(" + b.round3(projectionNow[0]) + "," + b.round3(projectionNow[5]) + ","
                        + b.round3(projectionNow[10]) + ")");
            }

            // A model is drawn through the lit ITEM pipeline, whose descriptor set needs the light map; with no mirror
            // the descriptor is 0 and the enqueue refuses. The terrain pass recovers the mirror before it gives up
            // (ensureLightmapMirror), and the model path has to do the same: the first-person arm is drawn before the
            // frame's first terrain pass and was the one refusal per plain leg - measured reason, leg gv-cohort-dl15:
            // "could not queue list 109 (prepareImmediate null (format=4 texture=108 (mirror has full content:
            // descriptor creation failed) pipeline=present))". The recovery is idempotent and does nothing once the
            // mirror exists, so this costs one null check per model draw in the steady state.
            if (b.lightmapTexture == null) {
                b.ensureLightmapMirror();
            }
            if (!celtium$enqueueModelMesh(mesh, texture, state, key, projectionRemapped, modelViewNow,
                    mesh.indexCount() / 6, b.celtium$envCombineColor())) {
                b.modelDrawRefusals++;
                b.modelLastRefusal = "could not queue list " + listId + " (" + b.modelQueueRefusal + ")";
                return false;
            }
            // Queued, not drawn: the draw happens in the window's pass, and the group counters (submitted/completed) are
            // what show that the queued batch was actually drawn. The caller cancels the GL replay on this basis.
            b.modelStats.noteReplay(b.lastModelSeen
                    && b.lastModelTexture == texture && b.lastModelStateKey == key
                    && java.util.Arrays.equals(b.lastModelProjection, projectionRemapped)
                    && java.util.Arrays.equals(b.lastModelViewMatrix, modelViewNow));
            System.arraycopy(projectionRemapped, 0, b.lastModelProjection, 0, 16);
            System.arraycopy(modelViewNow, 0, b.lastModelViewMatrix, 0, 16);
            b.lastModelTexture = texture;
            b.lastModelStateKey = key;
            b.lastModelSeen = true;
            b.modelDraws++;
            if (offTargetCall) {
                b.modelOffTargetDraws++;
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            b.modelDrawRefusals++;
            b.modelLastRefusal = String.valueOf(error);
            return false;
        } finally {
            // Timed only with the switch on: the question is whether a model replay's own CPU work is worth
            // attacking, or whether its drawn packet is all the frame pays for.
            if (b.BATCH_TIMINGS) b.modelStats.noteEnqueue(System.nanoTime() - modelEnqueueStart);
        }
    }

    boolean celtium$enqueueModelMesh(VulkanDynamicMesh mesh, int texture, int[] state, long key,
            float[] projection, float[] modelView, int quads, float[] combineColor) {
        if (b.MODEL_INSTANCES && mesh != null && !mesh.isEmpty()
                && celtium$appendModelInstance(mesh, texture, state, key, projection, modelView, quads, combineColor)) {
            return true;
        }
        if (mesh == null || mesh.isEmpty()) {
            // Named, because the caller reports every false return with one message: "could not queue list N" merged
            // "the mesh is empty", "prepareImmediate found no pass/mirror/descriptor" and "the cached pipeline is
            // null", and the fix differs for each.
            b.modelQueueRefusal = mesh == null ? "mesh absent" : "mesh empty";
            return false;
        }
        // ITEM, because that is the format whose pipeline evaluates the fixed-function lighting: its lit variant reads
        // the vertex normal and the push block's five vec4 (the two light directions in model space, the scene ambient
        // and each light's diffuse) and multiplies the result into the vertex colour. The vertex stride is the same 28
        // bytes the check at the top of prepareImmediate compares against, so the mesh built above is what it wants.
        PreparedImmediate prepared = b.prepareImmediate(VulkanImmediateFormats.ITEM, texture, key, state);
        if (prepared == null || prepared.pipeline == null) {
            b.modelQueueRefusal = prepared == null
                    ? "prepareImmediate null (" + b.lastPrepareFailure + ")"
                    : "pipeline null for format " + VulkanImmediateFormats.ITEM + " key=0x" + Long.toHexString(key)
                            + " texture=" + texture;
            return false;
        }
        long target = b.targetRegistry.currentIdentity();
        if (b.modelMatrixLoggedCount < 8) {
            // A handful of model enqueues, each saying which kind of projection it is drawn under. One row was not enough: the
            // residual image still showed screen-space quads, so the question is whether the projection is wrong at every
            // enqueue or only at some of them - and whether the modelview's rotation/scale rows are the ones the part was
            // compiled with. projection[11] is -1 for a GL perspective and 0 for an orthographic projection.
            b.modelMatrixLoggedCount++;
            System.out.println("[Celtium] CELTIUM_MODEL_ENQ texture=" + texture
                    // The target the group is stamped with, against the main identity: a model group whose target is not
                    // the one the window draws is kept in the queue instead of drawn (`immediateGroupsKeptForOtherTargets`),
                    // which is the shape of "accepted and never drawn" that the format histograms show for id4 and id9.
                    + " target=0x" + Long.toHexString(target) + " main=0x" + Long.toHexString(b.targetRegistry.mainIdentity())
                    + " mainBound=" + b.targetRegistry.currentIsMain()
                    + " projKind=" + (Math.abs(projection[11] + 1.0f) < 0.01f ? "perspective" : "ortho")
                    + " proj11=" + b.round3(projection[11]) + " proj10=" + b.round3(projection[10])
                    + " proj14=" + b.round3(projection[14])
                    + " mvT=" + b.round3(modelView[12]) + "," + b.round3(modelView[13]) + "," + b.round3(modelView[14])
                    + " mvRow0=" + b.round3(modelView[0]) + "," + b.round3(modelView[1]) + "," + b.round3(modelView[2])
                    + " mvScale=" + b.round3((float) Math.sqrt(modelView[0] * modelView[0] + modelView[1] * modelView[1]
                            + modelView[2] * modelView[2])));
            System.out.println("[Celtium] CELTIUM_MODEL_BOUNDS "
                    + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedBoundsSummary());
        }
        // Cloned: the replay hands in its scratch matrices, and a queued group keeps what it is given.
        projection = projection.clone();
        modelView = modelView.clone();
        ImmediateGroup group = ImmediateGroup.obtain(mesh, projection, modelView,
                VulkanImmediateFormats.ITEM, texture, key, state, target);
        group.prepared = prepared;
        group.combineColor = combineColor;
        // The current glColor, which the fixed pipeline multiplies into the vertex colour: the immediate path carries
        // it as the group colour for formats 6/7/9, and the ITEM replay needs the same feed - LayerSlimeGel's
        // glColor(1,1,1,0.25) was dropped here and drew the gel opaque white, and the hurt red never reached the model
        // vertices either. The lit vertex shader multiplies this into v_Color (skipped while a combine owns the
        // colour slots, so the red flash constant cannot tint twice).
        group.color = b.colorMirrorHasValue
                ? new float[] {b.immediateState.colorRed(), b.immediateState.colorGreen(),
                        b.immediateState.colorBlue(), b.immediateState.colorAlpha()}
                : null;
        group.texMatrix = b.celtium$textureAffine();
        group.lightParams = b.celtium$lightParams(state, modelView);
        // The same rule as the immediate path, and it is the light-map unit's enable state that decides it - not the
        // pass. The model path's GUI-stage content is the inventory's player model and any TESR item (a chest, a
        // shulker box, a banner: RenderItem.renderItem sends those through
        // getTileEntityItemStackRenderer().renderByItem, RenderItem.java:144-148), and real OpenGL draws both with the
        // unit disabled - GuiInventory.java:154-165 only *disables* the unit at the end of its draw and never enables
        // it, so nothing lights the player model by the world either. A chest in the hotbar of a dark place was drawn
        // with the cave's light because this line took the captured coordinate unconditionally.
        group.lightmapCoords = b.celtium$lightmapForGroup();
        group.fog = b.fog.snapshot();
        group.batches = quads;
        group.viewportValues = b.currentGroupViewportValues;
        // Borrowed: the mesh belongs to the capture that lent it and is drawn every frame, so the retire path
        // must not free it - which is the whole reason the stamp exists.
        group.stamp = GroupStamp.obtain(b.frameId, (int) b.targetRegistry.generation(), true, b.worldEpoch);
        // The model path creates its own group, so the per-identity texture census has to see it here as well: with the
        // census only at the immediate path's creation site the verification line listed a single identity (the loading
        // screen's) while the world and the GUI stage had both been drawn, which reads as "those stages never ran".
        if (b.TARGET_CENSUS_ENABLED) {
            b.celtium$noteTargetStage(target != Long.MIN_VALUE ? target : b.currentGroupTarget, texture, VulkanImmediateFormats.ITEM);
        }
        b.immediateGroups.add(group);
        b.immediateStatistics.noteImmediateGroupsClosed();
        if (b.GROUP_BOUNDS_DIAGNOSTICS) {
            // The model path creates its own group, so the bounds report has to be asked for here too: measured on leg
            // gv-cohort-dl16-bounds, a run filtered to format 4 spent its whole budget on the GUI groups (three per
            // frame) and never reported this one - the group the question was about. The line carries the projected
            // NDC bounds, which is what separates "outside the viewport" from "drawn and invisible".
            b.reports.reportGroupBounds(group);
        }
        if (target != b.targetRegistry.mainIdentity()) {
            // Off-target model batches are drawn in the pass that is open for their target, not carried in the queue.
            // The immediate window draws the groups of `currentRenderTarget` only (`flushImmediateWindow`: "others remain
            // owned in the queue") and runs on the main target later in the frame, so a group stamped with another
            // identity waits for a pass that belongs to it - which for the entity-outline framebuffer never comes,
            // because that framebuffer is cleared and consumed by the outline chain inside the same frame. That is the
            // offer path's own rule, written down as audit A10 ("the queue never sees a non-main group afterwards"), and
            // the model path has to follow it.
            //
            // Measured on the glow scene: 931 off-target model replays arrive from the outline pass
            // (`CELTIUM_MODEL_OFFTARGET list=125 texture=124 target=… main=… inTable=true verts=24`) - the vanilla
            // outline loop does replay the mobs' display lists - and with this flush each of them is recorded into the
            // identity the chain adopts (`…_FLUSH batches+6 flushes+1 groupsLeft=0`, against
            // `CELTIUM_OUTLINE_INPUT … identity=… texture=42`). Without it they sit in the queue: the visible frame
            // gains the outline pass's geometry (creeper green 1.017/0.971/1.003 with the flush, against 1.28/1.27/1.21
            // before it) and the outline image still holds only the item.
            // Coalesced to one flush per target change within a frame: the first off-target batch of a pass flushes the
            // batches of that pass, and the later batches of the same target/frame are left for the next change. Per
            // batch this was a submit and a wait each time (`frameIntervalP95Ms=1946`, world frames 43-63 -> 48).
            if (b.frameId != b.modelOffTargetFlushedFrame || target != b.modelOffTargetFlushedTarget) {
                b.modelOffTargetFlushedFrame = b.frameId;
                b.modelOffTargetFlushedTarget = target;
                long recordedBefore = b.immediateStatistics.immediateRecordedBatches();
                long flushesBefore = b.immediateStatistics.actualFlushes();
                b.flushPendingBeforeGlDraw();
                if (b.modelOffTargetFlushLogs < b.MODEL_OFFTARGET_FLUSH_LOG_LIMIT) {
                    b.modelOffTargetFlushLogs++;
                    System.out.println("[Celtium] CELTIUM_MODEL_OFFTARGET_FLUSH batches+"
                            + (b.immediateStatistics.immediateRecordedBatches() - recordedBefore)
                            + " flushes+" + (b.immediateStatistics.actualFlushes() - flushesBefore)
                            + " windowBusy=" + b.immediateWindowBusy
                            + " target=" + target + " groupsLeft=" + b.immediateGroups.size()
                            + " frame=" + b.frameId);
                }
            } else {
                b.modelOffTargetCoalesced++;
            }
        }
        return true;
    }

    /**
     * Appends a model replay to the native instance run, preserving its lighting and alpha-test semantics.
     * Off-target replays remain in their own pass on the per-group path.
     */
    /** {@code -Dceltium.debug.modelLightFrame=N}: print the lighting inputs of the first 80 model instances from frame N on. */
    private static final long MODEL_LIGHT_DEBUG_FRAME = Long.getLong("celtium.debug.modelLightFrame", 0L).longValue();
    private static int modelLightLines;

    boolean celtium$appendModelInstance(VulkanDynamicMesh mesh, int texture, int[] state, long key,
            float[] projection, float[] modelView, int quads, float[] combineColor) {
        if (b.modelInstancesFailed || state.length <= 14 || !b.context.textureArraySupported()) {
            return false;
        }
        long target = b.targetRegistry.currentIdentity();
        if (target != b.targetRegistry.mainIdentity() || (state[8] != 0 && b.lightmapTexture == null)) {
            return false;
        }
        if (b.textureRegistry == null) {
            b.textureRegistry = b.celtium$registry();
        }
        VulkanGlTexture image = b.textureRegistry.get(texture);
        int textureIndex = image == null ? -1 : b.celtium$registerTexture(image);
        int lightmapIndex = state[8] == 0 ? textureIndex : b.celtium$registerTexture(b.lightmapTexture);
        if (textureIndex < 0 || lightmapIndex < 0 || b.textureArray == null) {
            return false;
        }
        if (b.modelInstances == null) {
            VulkanModelInstances created = new VulkanModelInstances(b.context, b.textureArray.layout());
            try {
                if (!created.create()) {
                    created.delete();
                    b.modelInstancesFailed = true;
                    return false;
                }
            } catch (RuntimeException | LinkageError error) {
                System.out.println("[Celtium] CELTIUM_MODEL_INSTANCES create failed: " + error);
                created.delete();
                b.modelInstancesFailed = true;
                return false;
            }
            b.modelInstances = created;
            created.beginFrame(b.frameId);
        }
        VulkanModelInstances instances = b.modelInstances;
        instances.syncPool(b.modelGeometry.modelMeshes());
        int listId = mesh.ownerList();
        if (!instances.hasList(listId)) {
            instances.noteMissing();
            return false;
        }
        if (!instances.hasRoom()) {
            return false;
        }
        VulkanTerrainPipeline terrainPipeline = b.terrain.pipelineFor(b.lastUiPass);
        long pass = terrainPipeline != null ? terrainPipeline.renderPass() : b.uiRenderPass();
        int[] viewport = b.currentGroupViewportValues;
        ImmediateGroup open = b.openModelRunGroup;
        boolean extend = open != null && !b.immediateGroups.isEmpty()
                && b.immediateGroups.get(b.immediateGroups.size() - 1) == open
                && b.currentImmediateMesh == null
                && open.stateKey == key && open.target == target
                && java.util.Arrays.equals(open.projection, projection) && !b.fog.movedFrom(open.fog)
                && open.modelRun.pipeline.renderPass == pass
                && java.util.Arrays.equals(open.viewportValues, viewport)
                && open.stamp != null && open.stamp.frameId == b.frameId
                && instances.sameBuffers(open.modelRun);
        if (!extend) {
            VulkanModelInstances.Pipeline pipeline;
            try {
                pipeline = instances.pipelineFor(pass, key, b.celtium$blendState(state), state[3] != 0, state[4] != 0,
                        state[5] != 0, b.celtium$vkCompareOp(state[7]));
            } catch (RuntimeException | LinkageError error) {
                System.out.println("[Celtium] CELTIUM_MODEL_INSTANCES pipeline failed: " + error);
                b.modelInstancesFailed = true;
                return false;
            }
            // Whatever the immediate path holds open is older than this replay and must be queued before it.
            b.closeCurrentImmediateGroup();
            ImmediateGroup group = ImmediateGroup.obtain(mesh, projection.clone(), modelView.clone(), VulkanImmediateFormats.ITEM,
                    texture, key, state, target);
            group.modelRun = instances.openRun(pipeline);
            group.fog = b.fog.snapshot();
            group.viewportValues = viewport;
            group.stamp = GroupStamp.obtain(b.frameId, (int) b.targetRegistry.generation(), true, b.worldEpoch);
            b.immediateGroups.add(group);
            b.immediateStatistics.noteImmediateGroupsClosed();
            b.openModelRunGroup = group;
            open = group;
        }
        float[] colour = null;
        if (b.colorMirrorHasValue) {
            colour = b.modelInstanceColour;
            colour[0] = b.immediateState.colorRed();
            colour[1] = b.immediateState.colorGreen();
            colour[2] = b.immediateState.colorBlue();
            colour[3] = b.immediateState.colorAlpha();
        }
        float[] affine = b.celtium$readTextureAffine(b.modelInstanceAffine) ? b.modelInstanceAffine : null;
        float[] light = b.celtium$lightParams(state, modelView, b.modelInstanceLight);
        if (MODEL_LIGHT_DEBUG_FRAME > 0 && b.frameId >= MODEL_LIGHT_DEBUG_FRAME && modelLightLines++ < 80) {
            // One frame of the instances' lighting inputs, for a lit model whose shade differs from the reference:
            // the flag, the object-space light terms and the lightmap coordinate the shader will see, by list.
            float[] lm = b.celtium$lightmapForGroup();
            System.out.println("[Celtium] CELTIUM_MODEL_LIGHT list=" + listId + " lit=" + state[8] + " t=["
                    + modelView[12] + "," + modelView[13] + "," + modelView[14] + "] params="
                    + (light == null ? "null" : java.util.Arrays.toString(light)) + " lightmap="
                    + (lm == null ? "null" : java.util.Arrays.toString(lm)) + " colour="
                    + (colour == null ? "none" : java.util.Arrays.toString(colour)));
        }
        instances.append(open.modelRun, listId, modelView, colour, combineColor, affine, Float.intBitsToFloat(state[6]), state[14],
                light, b.celtium$lightmapForGroup(), textureIndex, lightmapIndex, texture);
        open.batches += quads;
        return true;
    }

    /**
     * Records one instance run in place of its group: the group's viewport, a zero depth bias (model replays carry
     * none), then the run's one indirect call. The run binds its own pipeline layout, so the bind cache and the shared
     * push cache are cleared for whatever group follows.
     */
    void celtium$drawModelRun(ImmediateGroup group) {
        if (b.openModelRunGroup == group) {
            b.openModelRunGroup = null;
        }
        b.celtium$flushIndirectRun();
        try (MemoryStack groupStack = MemoryStack.stackPush()) {
            b.celtium$setGroupViewport(groupStack, group);
        }
        if (b.passBiasC != 0.0F || b.passBiasS != 0.0F) {
            org.lwjgl.vulkan.VK10.vkCmdSetDepthBias(b.commandBuffer, 0.0F, 0.0F, 0.0F);
            b.passBiasC = 0.0F;
            b.passBiasS = 0.0F;
        }
        if (b.modelInstances != null && b.textureArray != null) {
            long textureSet = b.textureArray.set();
            long pipeline = group.modelRun.pipeline.handle;
            if (b.modelBindValid && b.modelBoundPipeline == pipeline && b.modelBoundTextureSet == textureSet) {
                b.modelInstances.drawCached(b.commandBuffer, group.modelRun, b.context.multiDrawIndirectEnabled(),
                        group.projection, group.fog);
            } else {
                b.modelInstances.draw(b.commandBuffer, group.modelRun, textureSet,
                        b.context.multiDrawIndirectEnabled(), group.projection, group.fog);
                b.modelBindValid = true;
                b.modelBoundPipeline = pipeline;
                b.modelBoundTextureSet = textureSet;
            }
        }
        java.util.Arrays.fill(b.immediateBindCache, -1L);
        VulkanPushCache.SHARED.invalidate();
        int batches = group.batches;
        b.immediateStatistics.noteGroupDrawCount();
        b.immediateStatistics.addImmediateRecordedBatches(batches);
        b.immediateStatistics.noteImmediateGroupsDrawn();
        b.immediateStatistics.noteImmediateFlushes();
        b.frameDrawn++;
        b.drawnByFormat[VulkanImmediateFormats.ITEM] += batches;
        b.recordedImmediateGroups.add(group);
        b.celtium$recordQueuedClearsInPass(group.postClears, group.target);
        group.postClears = null;
    }
}
