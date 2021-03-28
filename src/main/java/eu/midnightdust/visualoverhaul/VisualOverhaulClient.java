package eu.midnightdust.visualoverhaul;

import eu.midnightdust.visualoverhaul.block.JukeboxTop;
import eu.midnightdust.visualoverhaul.block.renderer.BrewingStandBlockEntityRenderer;
import eu.midnightdust.visualoverhaul.block.renderer.FurnaceBlockEntityRenderer;
import eu.midnightdust.visualoverhaul.block.renderer.JukeboxBlockEntityRenderer;
import eu.midnightdust.visualoverhaul.config.VOConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.impl.blockrenderlayer.BlockRenderLayerMapImpl;
import net.fabricmc.fabric.impl.client.rendering.ColorProviderRegistryImpl;
import net.fabricmc.fabric.impl.networking.ClientSidePacketRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BuiltinBiomes;

import java.util.Objects;

import static eu.midnightdust.visualoverhaul.VisualOverhaul.*;

@SuppressWarnings("deprecation")
public class VisualOverhaulClient implements ClientModInitializer {

    public static Block JukeBoxTop = new JukeboxTop();
    private final MinecraftClient client = MinecraftClient.getInstance();

    @Override
    public void onInitializeClient() {
        VOConfig.init("visualoverhaul", VOConfig.class);

        // Block only registered on client, because it's just used for the renderer //
        Registry.register(Registry.BLOCK, new Identifier("visualoverhaul","jukebox_top"), JukeBoxTop);

        BlockRenderLayerMapImpl.INSTANCE.putBlock(Blocks.JUKEBOX, RenderLayer.getCutout());
        BlockRenderLayerMapImpl.INSTANCE.putBlock(JukeBoxTop, RenderLayer.getCutout());
        BlockRenderLayerMapImpl.INSTANCE.putBlock(Blocks.FURNACE, RenderLayer.getCutout());

        BlockEntityRendererRegistry.INSTANCE.register(BlockEntityType.BREWING_STAND, BrewingStandBlockEntityRenderer::new);
        BlockEntityRendererRegistry.INSTANCE.register(BlockEntityType.JUKEBOX, JukeboxBlockEntityRenderer::new);
        BlockEntityRendererRegistry.INSTANCE.register(BlockEntityType.FURNACE, FurnaceBlockEntityRenderer::new);

        Registry.ITEM.forEach((item) -> {
            if(item instanceof MusicDiscItem) {
                FabricModelPredicateProviderRegistry.register(item, new Identifier("round"), (stack, world, entity) -> stack.getCount() == 2 ? 1.0F : 0.0F);
            }
        });

        ClientSidePacketRegistryImpl.INSTANCE.register(UPDATE_POTION_BOTTLES,
                (packetContext, attachedData) -> {
                    BlockPos pos = attachedData.readBlockPos();
                    DefaultedList<ItemStack> inv = DefaultedList.ofSize(5, ItemStack.EMPTY);
                    for (int i = 0; i < 4; i++) {
                        inv.set(i, attachedData.readItemStack());
                    }
                    packetContext.getTaskQueue().execute(() -> {
                        if (client.world != null && client.world.getBlockEntity(pos) != null && client.world.getBlockEntity(pos) instanceof BrewingStandBlockEntity) {
                            BrewingStandBlockEntity blockEntity = (BrewingStandBlockEntity) client.world.getBlockEntity(pos);
                            blockEntity.setStack(0, inv.get(0));
                            blockEntity.setStack(1, inv.get(1));
                            blockEntity.setStack(2, inv.get(2));
                            blockEntity.setStack(3, inv.get(3));
                            blockEntity.setStack(4, inv.get(4));
                        }
                    });
                });
        ClientSidePacketRegistryImpl.INSTANCE.register(UPDATE_RECORD,
                (packetContext, attachedData) -> {
                    BlockPos pos = attachedData.readBlockPos();
                    ItemStack record = attachedData.readItemStack();
                    packetContext.getTaskQueue().execute(() -> {
                        if (client.world != null && client.world.getBlockEntity(pos) != null && client.world.getBlockEntity(pos) instanceof JukeboxBlockEntity) {
                            JukeboxBlockEntity blockEntity = (JukeboxBlockEntity) client.world.getBlockEntity(pos);
                            blockEntity.setRecord(record);
                        }
                    });
                });
        ClientSidePacketRegistryImpl.INSTANCE.register(UPDATE_FURNACE_ITEMS,
                (packetContext, attachedData) -> {
                    BlockPos pos = attachedData.readBlockPos();
                    DefaultedList<ItemStack> inv = DefaultedList.ofSize(3, ItemStack.EMPTY);
                    for (int i = 0; i < 2; i++) {
                        inv.set(i, attachedData.readItemStack());
                    }
                    packetContext.getTaskQueue().execute(() -> {
                        if (client.world != null && client.world.getBlockEntity(pos) != null && client.world.getBlockEntity(pos) instanceof FurnaceBlockEntity) {
                            FurnaceBlockEntity blockEntity = (FurnaceBlockEntity) client.world.getBlockEntity(pos);
                            blockEntity.setStack(0, inv.get(0));
                            blockEntity.setStack(1, inv.get(1));
                            blockEntity.setStack(2, inv.get(2));
                        }
                    });
                });

        // Register builtin resourcepacks
        FabricLoader.getInstance().getModContainer("visualoverhaul").ifPresent(modContainer -> {
            ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("visualoverhaul:nobottles"), "resourcepacks/nobrewingbottles", modContainer, true);
            ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("visualoverhaul:fancyfurnace"), "resourcepacks/fancyfurnace", modContainer, true);
            ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("visualoverhaul:coloredwaterbucket"), "resourcepacks/coloredwaterbucket", modContainer, true);
        });

        // Context Colored Items
        if (VOConfig.coloredItems) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                int waterColor;
                int foliageColor;
                if (client.world != null) {
                    Biome biome = client.world.getBiome(client.player.getBlockPos());
                    waterColor = biome.getWaterColor();
                    foliageColor = biome.getFoliageColor();
                } else {
                    waterColor = BuiltinBiomes.PLAINS.getWaterColor();
                    foliageColor = BuiltinBiomes.PLAINS.getFoliageColor();
                }
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> waterColor, VisualOverhaul.Puddle);
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 0 ? -1 : waterColor, Items.WATER_BUCKET);
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> foliageColor, Items.GRASS_BLOCK);
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> foliageColor, Items.ACACIA_LEAVES);
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> foliageColor, Items.DARK_OAK_LEAVES);
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> foliageColor, Items.JUNGLE_LEAVES);
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> foliageColor, Items.OAK_LEAVES);
                ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
                    if (PotionUtil.getPotion(stack) == Potions.WATER && tintIndex == 0) {
                        return waterColor;
                    }
                    return tintIndex > 0 ? -1 : PotionUtil.getColor(stack);
                }, Items.POTION);
            });
        }
        // Else just register a static color for our puddle item
        else {
            ColorProviderRegistry.ITEM.register((stack, tintIndex) -> BuiltinBiomes.PLAINS.getWaterColor(), Puddle);
        }
        ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> Objects.requireNonNull(ColorProviderRegistryImpl.BLOCK.get(Blocks.WATER)).getColor(state, view, pos, tintIndex), Puddle);
    }
}