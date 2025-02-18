package corgitaco.betterweather.mixin.client;

import corgitaco.betterweather.api.season.Season;
import corgitaco.betterweather.helpers.BetterWeatherWorldData;
import corgitaco.betterweather.season.BWSeason;
import corgitaco.betterweather.season.BWSubseasonSettings;
import corgitaco.betterweather.season.SeasonContext;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.Util;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    private static final double ONE_THIRDS = 1.0 / 3;
    private static final double TWO_THIRDS = ONE_THIRDS * 2;

    @Shadow
    public abstract Item getItem();

    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void addCropFertility(PlayerEntity playerIn, ITooltipFlag advanced, CallbackInfoReturnable<List<ITextComponent>> cir) {
        if (playerIn == null) {
            return;
        }
        World world = playerIn.getEntityWorld();
        SeasonContext seasonContext = ((BetterWeatherWorldData) world).getSeasonContext();

        if (seasonContext == null) {
            return;
        }

        Item item = getItem();

        if (item instanceof BlockItem) {
            Block block = ((BlockItem) item).getBlock();
            BWSubseasonSettings currentSubSeasonSettings = seasonContext.getCurrentSubSeasonSettings();
            MutableRegistry<Biome> biomeRegistry = world.func_241828_r().getRegistry(Registry.BIOME_KEY);
            RegistryKey<Biome> currentBiomeKey = biomeRegistry.getOptionalKey(world.getBiome(playerIn.getPosition())).get();
            double currentSeasonBiomeCropGrowthMultiplier = currentSubSeasonSettings.getCropGrowthMultiplier(currentBiomeKey, block);

            if (currentSubSeasonSettings.getEnhancedCrops().contains(block)) {
                Minecraft mc = Minecraft.getInstance();

                List<ITextComponent> toolTips = cir.getReturnValue();
                if (InputMappings.isKeyDown(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindSneak.getKey().getKeyCode())) {
                    if(seasonContext.getCropFavoriteBiomeBonuses().containsKey(block)) {
                        Object2DoubleArrayMap<RegistryKey<Biome>> favoriteBiomes = seasonContext.getCropFavoriteBiomeBonuses().get(block);
                        if (!favoriteBiomes.isEmpty()) {

                            StringTextComponent favBiomes = new StringTextComponent(Arrays.toString(favoriteBiomes.keySet().stream().map(RegistryKey::getLocation).map(location -> new TranslationTextComponent(Util.makeTranslationKey("biome", location)).getString()).toArray()));
                            toolTips.add(new TranslationTextComponent("bwseason.cropfertility.tooltip.favoritebiomes", favBiomes.mergeStyle(TextFormatting.AQUA)));
                        }
                    }

                    toolTips.add(new TranslationTextComponent("bwseason.cropfertility.tooltip.season", getFertilityString(currentSeasonBiomeCropGrowthMultiplier)));
                } else {
                    toolTips.add(new TranslationTextComponent("bwseason.cropfertility.tooltip.hint.favbiomes", new TranslationTextComponent(mc.gameSettings.keyBindSneak.getTranslationKey())).mergeStyle(TextFormatting.DARK_GRAY, TextFormatting.ITALIC));
                }

                if (InputMappings.isKeyDown(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindSprint.getKey().getKeyCode())) {
                    IFormattableTextComponent favoriteSeason = null;
                    IFormattableTextComponent favoritePhase = null;
                    double bestMultiplier = Double.MIN_VALUE;

                    List<TranslationTextComponent> seasonFertilityTips = new ArrayList<>();
                    for (Season.Key key : Season.Key.values()) {
                        TranslationTextComponent keyTranslationTextComponent = key.translationTextComponent();

                        for (Season.Phase phase : Season.Phase.values()) {
                            TranslationTextComponent phaseTranslationTextComponent = phase.translationTextComponent();
                            BWSeason currentSeason = seasonContext.getSeasons().get(key);
                            BWSubseasonSettings bwSubseasonSettings = currentSeason.getPhaseSettings().get(phase);
                            double cropGrowthMultiplier = bwSubseasonSettings.getCropGrowthMultiplier(null, block);
                            if (cropGrowthMultiplier > bestMultiplier) {
                                bestMultiplier = cropGrowthMultiplier;
                                favoriteSeason = new TranslationTextComponent(keyTranslationTextComponent.getKey()).mergeStyle(TextFormatting.GREEN);
                                favoritePhase = new TranslationTextComponent(phaseTranslationTextComponent.getKey()).mergeStyle(TextFormatting.GREEN);
                            }

                            seasonFertilityTips.add(new TranslationTextComponent("bwseason.cropfertility.tooltip.seasons", keyTranslationTextComponent.mergeStyle(TextFormatting.AQUA), phaseTranslationTextComponent.mergeStyle(TextFormatting.AQUA), getFertilityString(cropGrowthMultiplier)));
                        }
                    }

                    if (favoriteSeason != null && favoritePhase != null) {
                        toolTips.add(new TranslationTextComponent("bwseason.cropfertility.tooltip.favoriteseason", favoriteSeason, favoritePhase));
                    }
                    toolTips.addAll(seasonFertilityTips);
                } else {
                    toolTips.add(new TranslationTextComponent("bwseason.cropfertility.tooltip.hint.seasonfertilities", new TranslationTextComponent(mc.gameSettings.keyBindSprint.getTranslationKey())).mergeStyle(TextFormatting.DARK_GRAY, TextFormatting.ITALIC));
                }
            }
        }
    }

    private static IFormattableTextComponent getFertilityString(double cropMultiplier) {
        if (cropMultiplier == 1.0) {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.normal");
        } else if (cropMultiplier < 0.0) {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.impossible").mergeStyle(TextFormatting.RED);
        } else if (cropMultiplier > 2.0) {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.excellent").mergeStyle(TextFormatting.GREEN);
        } else if (cropMultiplier > 1.5) {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.verygood").mergeStyle(TextFormatting.GREEN);
        } else if (cropMultiplier > 1.0) {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.good").mergeStyle(TextFormatting.GREEN);
        } else if (cropMultiplier > TWO_THIRDS) {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.notgood").mergeStyle(TextFormatting.YELLOW);
        } else if (cropMultiplier > ONE_THIRDS) {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.bad").mergeStyle(TextFormatting.RED);
        } else {
            return new TranslationTextComponent("bwseason.cropfertility.tooltip.fertility.undetermined").mergeStyle(TextFormatting.RED);

        }
    }
}
