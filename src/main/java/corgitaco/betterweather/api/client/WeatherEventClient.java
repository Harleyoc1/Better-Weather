package corgitaco.betterweather.api.client;

import com.mojang.blaze3d.systems.RenderSystem;
import corgitaco.betterweather.BetterWeather;
import corgitaco.betterweather.api.client.graphics.Graphics;
import corgitaco.betterweather.api.client.graphics.opengl.program.ShaderProgram;
import corgitaco.betterweather.api.client.graphics.opengl.program.ShaderProgramBuilder;
import corgitaco.betterweather.api.weather.WeatherEventClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class WeatherEventClient<T extends WeatherEventClientSettings> {

    private final ColorSettings colorSettings;
    private final float skyOpacity;
    private final float fogDensity;
    private final boolean sunsetSunriseColor;

    private ShaderProgram program;

    protected final BlockPos.Mutable mutable = new BlockPos.Mutable();

    public WeatherEventClient(T clientSettings) {
        this.colorSettings = clientSettings.getColorSettings();
        this.skyOpacity = clientSettings.skyOpacity();
        this.fogDensity = clientSettings.fogDensity();
        this.sunsetSunriseColor = clientSettings.sunsetSunriseColor();
    }

    public boolean renderWeather(Graphics graphics, Minecraft mc, ClientWorld world, LightTexture lightTexture, int ticks, float partialTicks, double x, double y, double z, Predicate<Biome> biomePredicate) {
        return graphics.isSupported() ?
                renderWeatherShaders(graphics, world, x, y, z) :
                renderWeatherLegacy(mc, world, lightTexture, ticks, partialTicks, x, y, z, biomePredicate);
    }

    public abstract boolean renderWeatherShaders(Graphics graphics, ClientWorld world, double x, double y, double z);

    public abstract boolean renderWeatherLegacy(Minecraft mc, ClientWorld world, LightTexture lightTexture, int ticks, float partialTicks, double x, double y,  double z, Predicate<Biome> biomePredicate);

    public abstract void clientTick(ClientWorld world, int tickSpeed, long worldTime, Minecraft mc, Predicate<Biome> biomePredicate);

    public boolean sunsetSunriseColor() {
        return sunsetSunriseColor;
    }

    public float skyOpacity() {
        return MathHelper.clamp(skyOpacity, 0.0F, 1.0F);
    }

    public float dayLightDarkness() {
        return fogDensity;
    }

    public boolean drippingLeaves() {
        return false;
    }

    public float fogDensity() {
        return fogDensity;
    }

    public ColorSettings getColorSettings() {
        return colorSettings;
    }

    @OnlyIn(Dist.CLIENT)
    public float skyOpacity(ClientWorld world, BlockPos playerPos, Predicate<Biome> isValidBiome) {
        return mixer(world, playerPos, 12, 2.0F, 1.0F - skyOpacity, isValidBiome);
    }

    @OnlyIn(Dist.CLIENT)
    public float fogDensity(ClientWorld world, BlockPos playerPos, Predicate<Biome> isValidBiome) {
        return mixer(world, playerPos, 12, 0.1F, fogDensity, isValidBiome);
    }


    private float mixer(ClientWorld world, BlockPos playerPos, int transitionRange, float weight, float targetMaxValue, Predicate<Biome> validBiomes) {
        int x = playerPos.getX();
        int z = playerPos.getZ();
        float accumulated = 0.0F;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int sampleX = x - transitionRange; sampleX <= x + transitionRange; ++sampleX) {
            pos.setX(sampleX);

            for (int sampleZ = z - transitionRange; sampleZ <= z + transitionRange; ++sampleZ) {
                pos.setZ(sampleZ);

                Biome biome = world.getBiome(pos);
                if (validBiomes.test(biome)) {

                    accumulated += weight * weight;
                }
            }
        }
        float transitionSmoothness = 33 * 33;
        return Math.min(targetMaxValue, (float) Math.sqrt(accumulated / transitionSmoothness));
    }

    @OnlyIn(Dist.CLIENT)
    public float cloudBlendStrength(ClientWorld world, BlockPos playerPos, Predicate<Biome> isValidBiome) {
        return mixer(world, playerPos, 15, 1.2F, (float) this.getColorSettings().getCloudColorBlendStrength(), isValidBiome);
    }

    public boolean weatherParticlesAndSound(ActiveRenderInfo renderInfo, Minecraft mc, float ticks, Predicate<Biome> validBiomes) {
        return true;
    }

    public void renderVanillaWeather(Minecraft mc, float partialTicks, double cameraX, double cameraY, double cameraZ, LightTexture lightmapIn, float[] rainSizeX, float[] rainSizeZ, ResourceLocation rainTexture, ResourceLocation snowTexture, int ticks, Predicate<Biome> isValidBiome) {
        float rainStrength = mc.world.getRainStrength(partialTicks);
        if (!(rainStrength <= 0.0F)) {
            lightmapIn.enableLightmap();
            World world = mc.world;
            int x = MathHelper.floor(cameraX);
            int y = MathHelper.floor(cameraY);
            int z = MathHelper.floor(cameraZ);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuffer();
            RenderSystem.enableAlphaTest();
            RenderSystem.disableCull();
            RenderSystem.normal3f(0.0F, 1.0F, 0.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.defaultAlphaFunc();
            RenderSystem.enableDepthTest();
            int weatherRenderDistanceInBlocks = 5;
            if (Minecraft.isFancyGraphicsEnabled()) {
                weatherRenderDistanceInBlocks = 10;
            }

            RenderSystem.depthMask(Minecraft.isFabulousGraphicsEnabled());
            int i1 = -1;
            float ticksAndPartialTicks = (float) ticks + partialTicks;
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            BlockPos.Mutable mutable = new BlockPos.Mutable();

            for (int dz = z - weatherRenderDistanceInBlocks; dz <= z + weatherRenderDistanceInBlocks; ++dz) {
                for (int dx = x - weatherRenderDistanceInBlocks; dx <= x + weatherRenderDistanceInBlocks; ++dx) {
                    int index = (dz - z + 16) * 32 + dx - x + 16;
                    double rainX = (double) rainSizeX[index] * 0.5D;
                    double rainZ = (double) rainSizeZ[index] * 0.5D;
                    mutable.setPos(dx, y, dz);
                    Biome biome = world.getBiome(mutable);
                    if (!isValidBiome.test(biome)) {
                        continue;
                    }

                    if (biome.getPrecipitation() != Biome.RainType.NONE) {
                        int motionBlockingHeight = world.getHeight(Heightmap.Type.MOTION_BLOCKING, mutable).getY();
                        int belowCameraYWeatherRenderDistance = y - weatherRenderDistanceInBlocks;
                        int aboveCameraYWeatherRenderDistance = y + weatherRenderDistanceInBlocks;
                        if (belowCameraYWeatherRenderDistance < motionBlockingHeight) {
                            belowCameraYWeatherRenderDistance = motionBlockingHeight;
                        }

                        if (aboveCameraYWeatherRenderDistance < motionBlockingHeight) {
                            aboveCameraYWeatherRenderDistance = motionBlockingHeight;
                        }

                        int atAboveHeightY = Math.max(motionBlockingHeight, y);

                        if (belowCameraYWeatherRenderDistance != aboveCameraYWeatherRenderDistance) {
                            Random random = new Random((long) (dx * dx * 3121 + dx * 45238971 ^ dz * dz * 418711 + dz * 13761));
                            mutable.setPos(dx, belowCameraYWeatherRenderDistance, dz);
                            float biomeTemperature = biome.getTemperature(mutable);
                            if (biomeTemperature >= 0.15F) {
                                i1 = renderRain(mc, partialTicks, cameraX, cameraY, cameraZ, rainTexture, ticks, rainStrength, world, tessellator, bufferbuilder, (float) weatherRenderDistanceInBlocks, i1, mutable, dz, dx, rainX, rainZ, belowCameraYWeatherRenderDistance, aboveCameraYWeatherRenderDistance, atAboveHeightY, random);
                            } else {
                                i1 = renderSnow(mc, partialTicks, cameraX, cameraY, cameraZ, snowTexture, ticks, rainStrength, world, tessellator, bufferbuilder, (float) weatherRenderDistanceInBlocks, i1, ticksAndPartialTicks, mutable, dz, dx, rainX, rainZ, belowCameraYWeatherRenderDistance, aboveCameraYWeatherRenderDistance, atAboveHeightY, random);
                            }
                        }
                    }
                }
            }

            if (i1 >= 0) {
                tessellator.draw();
            }

            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultAlphaFunc();
            RenderSystem.disableAlphaTest();
            lightmapIn.disableLightmap();
        }
    }

    private int renderSnow(Minecraft mc, float partialTicks, double x, double y, double z, ResourceLocation snowTexture, int ticks, float rainStrength, World world, Tessellator tessellator, BufferBuilder bufferbuilder, float graphicsQuality, int i1, float f1, BlockPos.Mutable mutable, int dz, int dx, double d0, double d1, int j2, int k2, int atOrAboveY, Random random) {
        if (i1 != 1) {
            if (i1 >= 0) {
                tessellator.draw();
            }

            i1 = 1;
            mc.getTextureManager().bindTexture(snowTexture);
            bufferbuilder.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        }

        float f6 = -((float) (ticks & 511) + partialTicks) / 512.0F;
        float f7 = (float) (random.nextDouble() + (double) f1 * 0.01D * (double) ((float) random.nextGaussian()));
        float f8 = (float) (random.nextDouble() + (double) (f1 * (float) random.nextGaussian()) * 0.001D);
        double d3 = (double) ((float) dx + 0.5F) - x;
        double d5 = (double) ((float) dz + 0.5F) - z;
        float f9 = MathHelper.sqrt(d3 * d3 + d5 * d5) / graphicsQuality;
        float alpha = ((1.0F - f9 * f9) * 0.3F + 0.5F) * rainStrength;
        mutable.setPos(dx, atOrAboveY, dz);
        int combinedLight = WorldRenderer.getCombinedLight(world, mutable);
        int l3 = combinedLight >> 16 & '\uffff';
        int i4 = (combinedLight & '\uffff') * 3;
        int j4 = (l3 * 3 + 240) / 4;
        int k4 = (i4 * 3 + 240) / 4;
        bufferbuilder.pos((double) dx - x - d0 + 0.5D, (double) k2 - y, (double) dz - z - d1 + 0.5D).tex(0.0F + f7, (float) j2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, alpha).lightmap(k4, j4).endVertex();
        bufferbuilder.pos((double) dx - x + d0 + 0.5D, (double) k2 - y, (double) dz - z + d1 + 0.5D).tex(1.0F + f7, (float) j2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, alpha).lightmap(k4, j4).endVertex();
        bufferbuilder.pos((double) dx - x + d0 + 0.5D, (double) j2 - y, (double) dz - z + d1 + 0.5D).tex(1.0F + f7, (float) k2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, alpha).lightmap(k4, j4).endVertex();
        bufferbuilder.pos((double) dx - x - d0 + 0.5D, (double) j2 - y, (double) dz - z - d1 + 0.5D).tex(0.0F + f7, (float) k2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, alpha).lightmap(k4, j4).endVertex();
        return i1;
    }

    private int renderRain(Minecraft mc, float partialTicks, double x, double y, double z, ResourceLocation rainTexture, int ticks, float rainStrength, World world, Tessellator tessellator, BufferBuilder bufferbuilder, float l, int i1, BlockPos.Mutable mutable, int dz, int dx, double d0, double d1, int j2, int k2, int atOrAboveY, Random random) {
        if (i1 != 0) {
            if (i1 >= 0) {
                tessellator.draw();
            }

            i1 = 0;
            mc.getTextureManager().bindTexture(rainTexture);
            bufferbuilder.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        }

        int i3 = ticks + dx * dx * 3121 + dx * 45238971 + dz * dz * 418711 + dz * 13761 & 31;
        float f3 = -((float) i3 + partialTicks) / 32.0F * (3.0F + random.nextFloat());
        double d2 = (double) ((float) dx + 0.5F) - x;
        double d4 = (double) ((float) dz + 0.5F) - z;
        float f4 = MathHelper.sqrt(d2 * d2 + d4 * d4) / l;
        float alpha = ((1.0F - f4 * f4) * 0.5F + 0.5F) * rainStrength;
        mutable.setPos(dx, atOrAboveY, dz);
        int combinedLight = WorldRenderer.getCombinedLight(world, mutable);
        bufferbuilder.pos((double) dx - x - d0 + 0.5D, (double) k2 - y, (double) dz - z - d1 + 0.5D).tex(0.0F, (float) j2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, alpha).lightmap(combinedLight).endVertex();
        bufferbuilder.pos((double) dx - x + d0 + 0.5D, (double) k2 - y, (double) dz - z + d1 + 0.5D).tex(1.0F, (float) j2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, alpha).lightmap(combinedLight).endVertex();
        bufferbuilder.pos((double) dx - x + d0 + 0.5D, (double) j2 - y, (double) dz - z + d1 + 0.5D).tex(1.0F, (float) k2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, alpha).lightmap(combinedLight).endVertex();
        bufferbuilder.pos((double) dx - x - d0 + 0.5D, (double) j2 - y, (double) dz - z - d1 + 0.5D).tex(0.0F, (float) k2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, alpha).lightmap(combinedLight).endVertex();
        return i1;
    }

    public ShaderProgram buildOrGetProgram(Consumer<ShaderProgramBuilder> consumer) {
        if (program == null) {
            ShaderProgramBuilder builder = ShaderProgramBuilder.create();

            try {
                consumer.accept(builder);
            } catch (Exception e) {
                BetterWeather.LOGGER.error(e);

                builder.clean();
            }

            return program = builder.build();
        }

        return program;
    }
}
