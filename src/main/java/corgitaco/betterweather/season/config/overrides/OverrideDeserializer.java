package corgitaco.betterweather.season.config.overrides;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import corgitaco.betterweather.BetterWeather;
import corgitaco.betterweather.season.storage.OverrideStorage;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class OverrideDeserializer implements JsonDeserializer<BiomeToOverrideStorageJsonStorage> {

    private final Registry<Biome> biomeRegistry;
    private final IdentityHashMap<RegistryKey<Biome>, OverrideStorage> biomeOverrideStorage;
    private final IdentityHashMap<Block, Double> cropToMultiplierMap;
    private final boolean isClient;

    public OverrideDeserializer(Registry<Biome> biomeRegistry, IdentityHashMap<RegistryKey<Biome>, OverrideStorage> biomeOverrideStorage, IdentityHashMap<Block, Double> cropToMultiplierMap, boolean isClient) {
        this.biomeRegistry = biomeRegistry;
        this.biomeOverrideStorage = biomeOverrideStorage;
        this.cropToMultiplierMap = cropToMultiplierMap;
        this.isClient = isClient;
    }

    public void processKeys(ObjectOpenHashSet<Pair<Object, JsonElement>> processedJson) {
        Map<Biome.Category, List<Biome>> categoryListMap = biomeRegistry.getEntries().stream().map(Map.Entry::getValue).collect(Collectors.groupingBy(Biome::getCategory));
        Map<BiomeDictionary.Type, List<Biome>> biomeDictionaryMap = biomeRegistry.getEntries().stream()
                .flatMap(biomeKey -> BiomeDictionary.getTypes(biomeKey.getKey()).stream().map(biomeDictionaryType -> new AbstractMap.SimpleEntry<>(biomeDictionaryType, biomeKey.getValue())))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));


        for (Pair<Object, JsonElement> pair : processedJson) {
            Object object = pair.getFirst();
            if (object instanceof BiomeDictionary.Type) {
                for (Biome biome : biomeDictionaryMap.get(object)) {
                    ResourceLocation biomeID = biomeRegistry.getKey(biome);
                    if (biomeID != null) {
                        RegistryKey<Biome> biomeKey = RegistryKey.getOrCreateKey(Registry.BIOME_KEY, biomeID);
                        OverrideStorage overrideStorage = this.biomeOverrideStorage.getOrDefault(biomeKey, new OverrideStorage());
                        updateOverrideStorageData(overrideStorage, pair.getSecond(), isClient);
                        this.biomeOverrideStorage.put(biomeKey, overrideStorage);
                    }

                }
            } else if (object instanceof Biome.Category) {
                for (Biome biome : categoryListMap.get(object)) {
                    ResourceLocation biomeID = biomeRegistry.getKey(biome);
                    if (biomeID != null) {
                        RegistryKey<Biome> biomeKey = RegistryKey.getOrCreateKey(Registry.BIOME_KEY, biomeID);
                        OverrideStorage overrideStorage = this.biomeOverrideStorage.getOrDefault(biomeKey, new OverrideStorage());
                        updateOverrideStorageData(overrideStorage, pair.getSecond(), isClient);
                        this.biomeOverrideStorage.put(biomeKey, overrideStorage);
                    }
                }
            } else if (object instanceof Biome) {
                Biome biome = (Biome) object;
                ResourceLocation biomeID = biomeRegistry.getKey(biome);
                if (biomeID != null) {
                    RegistryKey<Biome> biomeKey = RegistryKey.getOrCreateKey(Registry.BIOME_KEY, biomeID);
                    OverrideStorage overrideStorage = this.biomeOverrideStorage.getOrDefault(biomeKey, new OverrideStorage());
                    updateOverrideStorageData(overrideStorage, pair.getSecond(), isClient);
                    this.biomeOverrideStorage.put(biomeKey, overrideStorage);
                }
            }
        }
    }

    public static void updateOverrideStorageData(OverrideStorage storage, JsonElement element, boolean isClient) {
        JsonObject jsonObject = element.getAsJsonObject();

        if (!isClient) {
            processOverrides(storage, jsonObject);
            processCropOverrides(storage, jsonObject);
        }
        processClientOverrides(storage, element);
    }

    private static void processOverrides(OverrideStorage storage, JsonObject jsonObject) {
        if (jsonObject.has("tempModifier")) {
            storage.setTempModifier(jsonObject.get("tempModifier").getAsDouble());
        }
        if (jsonObject.has("humidityModifier")) {
            storage.setHumidityModifier(jsonObject.get("humidityModifier").getAsDouble());
        }
        if (jsonObject.has("cropGrowthMultiplier")) {
            storage.setFallBack(jsonObject.get("cropGrowthMultiplier").getAsDouble());
        }
    }

    private static void processCropOverrides(OverrideStorage storage, JsonObject jsonObject) {
        if (jsonObject.has("cropOverrides")) {
            JsonObject cropOverrides = jsonObject.get("cropOverrides").getAsJsonObject();
            IdentityHashMap<Block, Double> cropMulitplierMap = new IdentityHashMap<>();
            for (Map.Entry<String, JsonElement> jsonElementEntry : cropOverrides.entrySet()) {
                Optional<Block> blockOptional = Registry.BLOCK.getOptional(new ResourceLocation(jsonElementEntry.getKey()));

                if (blockOptional.isPresent())
                    cropMulitplierMap.put(blockOptional.get(), jsonElementEntry.getValue().getAsDouble());
                else
                    BetterWeather.LOGGER.error("Block ID: \"" + jsonElementEntry + "\" is not a valid block ID in the registry, the override will not be applied...");

            }
            storage.setBlockToCropGrowthMultiplierMap(cropMulitplierMap);
        }
    }

    private static void processClientOverrides(OverrideStorage storage, JsonElement element) {
        if (element.getAsJsonObject().has("client")) {
            JsonObject client = element.getAsJsonObject().get("client").getAsJsonObject();

            if (client.has("targetFoliageHexColor")) {
                storage.getClientStorage().setTargetFoliageHexColor(client.get("targetFoliageHexColor").getAsString());
            }
            if (client.has("foliageColorBlendStrength")) {
                storage.getClientStorage().setFoliageColorBlendStrength(client.get("foliageColorBlendStrength").getAsDouble());
            }
            if (client.has("targetGrassHexColor")) {
                storage.getClientStorage().setTargetGrassHexColor(client.get("targetGrassHexColor").getAsString());
            }
            if (client.has("grassColorBlendStrength")) {
                storage.getClientStorage().setGrassColorBlendStrength(client.get("grassColorBlendStrength").getAsDouble());
            }
            if (client.has("targetSkyHexColor")) {
                storage.getClientStorage().setTargetSkyHexColor(client.get("targetSkyHexColor").getAsString());
            }
            if (client.has("skyColorBlendStrength")) {
                storage.getClientStorage().setSkyColorBlendStrength(client.get("skyColorBlendStrength").getAsDouble());
            }
            if (client.has("targetFogHexColor")) {
                storage.getClientStorage().setTargetFogHexColor(client.get("targetFogHexColor").getAsString());
            }
            if (client.has("fogColorBlendStrength")) {
                storage.getClientStorage().setFogColorBlendStrength(client.get("fogColorBlendStrength").getAsDouble());
            }
            storage.getClientStorage().parseHexColors();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public BiomeToOverrideStorageJsonStorage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject object = json.getAsJsonObject();
        StringBuilder errorBuilder = new StringBuilder();

        ObjectOpenHashSet<Pair<Object, JsonElement>> biomeObjects = new ObjectOpenHashSet<>();

        Map uncastedCropOverrides = null;

        Set<Map.Entry<String, JsonElement>> entrySet = object.entrySet();

        for (Map.Entry<String, JsonElement> entry : entrySet) {
            if (entry.getKey().equals("cropOverrides")) {
                uncastedCropOverrides = new Gson().fromJson(new Gson().toJson(entry.getValue()), Map.class);
                continue;
            }


            String key = entry.getKey();
            Object value = extractKey(errorBuilder, key, biomeRegistry);

            if (value == null)
                continue;

            biomeObjects.add(new Pair<>(value, entry.getValue()));

        }

        if (uncastedCropOverrides == null)
            uncastedCropOverrides = new HashMap();

        if (!errorBuilder.toString().isEmpty()) {
            throw new IllegalArgumentException("Errors were found in your override file: " + errorBuilder.toString());
        }


        processKeys(biomeObjects);
        return new BiomeToOverrideStorageJsonStorage(this.biomeOverrideStorage, isClient ? this.cropToMultiplierMap : processCropToMultiplierMap(uncastedCropOverrides));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private IdentityHashMap<Block, Double> processCropToMultiplierMap(Map uncastedCropOverrides) {
        IdentityHashMap<Block, Double> cropToMultiplierMap = new IdentityHashMap<>();
        if (!uncastedCropOverrides.isEmpty()) {
            IdentityHashMap<String, Double> blockIDToMultiplierMap = new IdentityHashMap<>(uncastedCropOverrides);
            blockIDToMultiplierMap.forEach((blockId, multiplier) -> {
                Optional<Block> blockOptional = Registry.BLOCK.getOptional(new ResourceLocation(blockId));

                if (blockOptional.isPresent())
                    cropToMultiplierMap.put(blockOptional.get(), multiplier);
                else
                    BetterWeather.LOGGER.error("Block ID: \"" + blockId + "\" is not a valid block ID in the registry, the override will not be applied...");
            });
        }
        return cropToMultiplierMap;
    }

    @Nullable
    public static Object extractKey(StringBuilder errorBuilder, String key, Registry<Biome> biomeRegistry) {
        String lowerCaseKey = key.toLowerCase();
        Object value;
        if (lowerCaseKey.startsWith("category/")) {
            try {
                value = Biome.Category.valueOf(lowerCaseKey.substring("category/".length()).toUpperCase());
            } catch (IllegalArgumentException e) {
                errorBuilder.append(key.substring("category/".length())).append(" is not a Biome Category Value! Valid category values: " + Arrays.toString(Biome.Category.values()) + "\n");
                return null;
            }
        } else if (lowerCaseKey.startsWith("forge/")) {
            value = BiomeDictionary.Type.getType(lowerCaseKey.substring("forge/".length()).toUpperCase());
        } else if (lowerCaseKey.startsWith("biome/")) {
            value = biomeRegistry.getOptional(new ResourceLocation(lowerCaseKey.substring("biome/".length()))).orElse(null);
            if (value == null) {
                errorBuilder.append(lowerCaseKey.substring("biome/".length())).append(" is not a biome in this world!\n");
                return null;
            }
        } else {
            errorBuilder.append(key).append(" is not a Biome/Category/Forge identifier\n");
            return null;
        }
        return value;
    }


    public static class ObjectToOverrideStorageJsonStorageSerializer implements JsonSerializer<BiomeToOverrideStorageJsonStorage.ObjectToOverrideStorageJsonStorage> {

        @Override
        public JsonElement serialize(BiomeToOverrideStorageJsonStorage.ObjectToOverrideStorageJsonStorage src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            for (Map.Entry<Object, OverrideStorage> entry : src.getObjectToOverrideStorage().entrySet()) {
                Object object = entry.getKey();
                OverrideStorage overrideStorage = entry.getValue();
                if (object instanceof Biome.Category) {
                    Biome.Category category = (Biome.Category) object;
                    result.add("category/" + category.toString(), overrideStorageJsonObject(overrideStorage));
                } else if (object instanceof BiomeDictionary.Type) {
                    BiomeDictionary.Type type = (BiomeDictionary.Type) object;
                    result.add("forge/" + type.toString(), overrideStorageJsonObject(overrideStorage));
                } else if (object instanceof ResourceLocation) {
                    ResourceLocation location = (ResourceLocation) object;
                    result.add("biome/" + location.toString(), overrideStorageJsonObject(overrideStorage));
                } else {
                    BetterWeather.LOGGER.error("Could not serialize object of class type: " + object.getClass().getName());
                }
            }
            return result;
        }

        public JsonObject overrideStorageJsonObject(OverrideStorage storage) {
            JsonObject jsonObject = new JsonObject();

            if (storage.getTempModifier() != Double.MAX_VALUE)
                jsonObject.addProperty("tempModifier", storage.getTempModifier());
            if (storage.getHumidityModifier() != Double.MAX_VALUE)
                jsonObject.addProperty("humidityModifier", storage.getHumidityModifier());
            if (storage.getFallBack() != Double.MAX_VALUE)
                jsonObject.addProperty("cropMultiplierDefault", storage.getFallBack());
            if (!storage.getBlockToCropGrowthMultiplierMap().isEmpty())
                jsonObject.add("cropOverrides", new Gson().toJsonTree(storage.getBlockToCropGrowthMultiplierMap(), Map.class));

            getClientJsonData(storage, jsonObject);

            return jsonObject;
        }

        private void getClientJsonData(OverrideStorage storage, JsonObject jsonObject) {
            OverrideStorage.OverrideClientStorage clientStorage = storage.getClientStorage();
            if (clientStorage != null) {
                JsonObject clientJsonObject = new JsonObject();
                if (!clientStorage.getTargetFoliageHexColor().isEmpty()) {
                    clientJsonObject.addProperty("targetFoliageHexColor", clientStorage.getTargetFoliageHexColor());
                }
                if (clientStorage.getFoliageColorBlendStrength() != Double.MAX_VALUE) {
                    clientJsonObject.addProperty("foliageColorBlendStrength", clientStorage.getFoliageColorBlendStrength());
                }

                if (!clientStorage.getTargetGrassHexColor().isEmpty()) {
                    clientJsonObject.addProperty("targetGrassHexColor", clientStorage.getTargetGrassHexColor());
                }
                if (clientStorage.getGrassColorBlendStrength() != Double.MAX_VALUE) {
                    clientJsonObject.addProperty("grassColorBlendStrength", clientStorage.getGrassColorBlendStrength());
                }

                if (!clientStorage.getTargetSkyHexColor().isEmpty()) {
                    clientJsonObject.addProperty("targetSkyHexColor", clientStorage.getTargetSkyHexColor());
                }
                if (clientStorage.getSkyColorBlendStrength() != Double.MAX_VALUE) {
                    clientJsonObject.addProperty("skyColorBlendStrength", clientStorage.getSkyColorBlendStrength());
                }

                if (!clientStorage.getTargetFogHexColor().isEmpty()) {
                    clientJsonObject.addProperty("targetFogHexColor", clientStorage.getTargetFogHexColor());
                }
                if (clientStorage.getFogColorBlendStrength() != Double.MAX_VALUE) {
                    clientJsonObject.addProperty("fogColorBlendStrength", clientStorage.getFogColorBlendStrength());
                }
                jsonObject.add("client", clientJsonObject);
            }
        }
    }
}
