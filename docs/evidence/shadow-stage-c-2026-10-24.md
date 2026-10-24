# 影 stage (c)+(d計器) の実機証拠 — 2026-10-24

## 出自

- HEAD `33bd277c6f4a8dbd1e6b9046ae2ee8b6b28ff5ce` + 影stage (c)(d)の作業差分（下記commitに一致）
- stage: `.gv-verify/jars/shadow-stage-c-20261024/build-provenance.json`
- renderer JAR sha256 `c6a21a4c5a2cf5b34087c61a731144c0dc005db515491d5c2855365aabc1c41d`
- validation-client JAR sha256 `d60c6e2be57e3bb996678143e81ac79d7fa1891a8c262043b420d0f91e7187bb`
- CPU-contract: `native_raster_adapter_check.py` PASS（9 LWJGL native宣言、同JAR）

## run

- leg `shadowc-amd-1`（`.gv-verify/run-shadowc-amd-1/`）、`.gv-verify/shadowc-amd-1-result.json`: accepted=true / exitCode 0 / verdict PASSED / problems []
- GPU: `AMD Radeon RX 6400 (RADV NAVI24)`（result JSONのnativeDeviceName、discrete-gpu、API 1.4.354、depthFormat D32_SFLOAT）
- scene: world（QMAGNET's Test Map、saveSlot=qmagnet、renderDistance 18、較正key一致 `sceneGateCalibrated=true`、`worldFrameGate` CALIBRATED vs run-qmref4）
- flags: `-Dceltium.vulkan.shadowMap=true -Dceltium.vulkan.shadowDumpDir=…shadow-dumps-shadowc-amd-1 -Dceltium.vulkan.shadowDumpEvery=100 -Dceltium.validation.fixedWorldTime=true -Dceltium.validation.worldTime=1500`（leg既定のpresentedDump/presentReadback込み）
- `celtium-validation.json`: status PASSED / worldTicksAtEnd 216 / worldFrames 284 / `presentedImageVerdict=PASSED`

## 計器出力（validation-console.log）

```
CELTIUM_SHADOW_TERRAIN frame=4 stride=32 draws=0 total=0
CELTIUM_SHADOW_DUMP present=100 … coverage=0.06813955 minDepth=0.39600754 meanCoveredDepth=0.4369 written=true
CELTIUM_SHADOW_TERRAIN frame=120 stride=32 draws=25 total=2778
CELTIUM_SHADOW_DUMP present=200 … （present=100と同一統計・同一バイト数）
```

- PNG: `.gv-verify/shadow-dumps-shadowc-amd-1/shadow-100.png` / `shadow-200.png`（2048²、明るい=光に近い、黒=clear 1.0）
  ASCII縮小で地形構造（塊+尾根状の輪郭）を確認。coverage 6.81%は halfExtent 64 のortho箱+周辺地形の足跡として過小でない。
- 両dumpの同一性: fixedWorldTime + held camera + ハードコート光源行列のため決定論的に同一。想定どおり。
- Vulkan validation: consoleにVUID違反なし。layout往復（SHADER_READ_ONLY→TRANSFER_SRC→SHADER_READ_ONLY）はoverloadで明示。

## 判定と範囲

- **言えること**: solid pass直前の深度専用passが同一chunk bindingで記録され、ライト視点の深度マップが実GPUで埋まる（stride=32 vanilla頂点経路）。
  メイン画像は較正gate・presented gateともPASSED（影passを足しても主画像が壊れない）。
- **言えないこと**: 24byte COMPACT経路の実GPU確認（decode照合のみ）。影の視覚効果（PCF未実装・presented画像は影なしと同じ）。
  光源行列は固定値（太陽方向 (0.3,0.9,0.2)、center (0,-16,0)、halfExtent 64、depthRange 256）でゲーム内時刻・方位は未接続。
