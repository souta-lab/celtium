package org.soutalab.celtium.shader.texture.mipmap;


public class ChannelMipmapGenerator extends AbstractMipmapGenerator {
	protected final BlendFunction redFunc;
	protected final BlendFunction greenFunc;
	protected final BlendFunction blueFunc;
	protected final BlendFunction alphaFunc;

	public ChannelMipmapGenerator(BlendFunction redFunc, BlendFunction greenFunc, BlendFunction blueFunc, BlendFunction alphaFunc) {
		this.redFunc = redFunc;
		this.greenFunc = greenFunc;
		this.blueFunc = blueFunc;
		this.alphaFunc = alphaFunc;
	}

	@Override
	public int blend(int c0, int c1, int c2, int c3) {
		return PixelImage.combine(
				alphaFunc.blend(PixelImage.getA(c0), PixelImage.getA(c1), PixelImage.getA(c2), PixelImage.getA(c3)),
				blueFunc.blend(PixelImage.getB(c0), PixelImage.getB(c1), PixelImage.getB(c2), PixelImage.getB(c3)),
				greenFunc.blend(PixelImage.getG(c0), PixelImage.getG(c1), PixelImage.getG(c2), PixelImage.getG(c3)),
				redFunc.blend(PixelImage.getR(c0), PixelImage.getR(c1), PixelImage.getR(c2), PixelImage.getR(c3))
		);
	}

	public interface BlendFunction {
		int blend(int v0, int v1, int v2, int v3);
	}
}
