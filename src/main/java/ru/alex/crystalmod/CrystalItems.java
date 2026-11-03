package ru.alex.crystalmod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class CrystalItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrystalMod.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CrystalMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CrystalMod.MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CrystalMod.MOD_ID);

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CrystalMod.MOD_ID);
    public static final DeferredBlock<MarketStallBlock> MARKET_STALL = BLOCKS.registerBlock("market_stall", MarketStallBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> MARKET_STALL_ITEM = ITEMS.registerSimpleBlockItem(MARKET_STALL);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<MarketStallEntity>> MARKET_STALL_ENTITY =
            BLOCK_ENTITIES.register("market_stall", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(MarketStallEntity::new, MARKET_STALL.get()).build(null));

    /** Банкомат — ПКМ открывает {@link CrystalATMMenu} (внести/снять). */
    public static final DeferredBlock<CrystalATMBlock> ATM = BLOCKS.registerBlock(
            "atm",
            CrystalATMBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 6)
                    .requiresCorrectToolForDrops()
    );
    public static final DeferredItem<BlockItem> ATM_ITEM = ITEMS.registerSimpleBlockItem(ATM);

    /** Терминал рынка — ПКМ открывает {@link MarketListingMenu} (выставить лот). */
    public static final DeferredBlock<MarketTerminalBlock> MARKET_TERMINAL = BLOCKS.registerBlock(
            "market_terminal",
            MarketTerminalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 6)
                    .requiresCorrectToolForDrops()
    );
    public static final DeferredItem<BlockItem> MARKET_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(MARKET_TERMINAL);

    /** {serial, value} банкноты — см. {@link CrystalNoteContents}. */
    public static final Supplier<DataComponentType<CrystalNoteContents>> CRYSTAL_NOTE_CONTENTS =
            DATA_COMPONENTS.register("crystal_note_contents",
                    () -> DataComponentType.<CrystalNoteContents>builder()
                            .persistent(CrystalNoteContents.CODEC)
                            .networkSynchronized(CrystalNoteContents.STREAM_CODEC)
                            .build());

    /**
     * Банкнота хрусталиков. {@code stacksTo(1)} — подстраховка: разные serial'ы
     * и так не дают компонентам совпасть, но явный предел не даёт на это неявно полагаться.
     * {@code fireResistant} — накопленное не должно сгорать в лаве.
     */
    public static final DeferredItem<CrystalNoteItem> CRYSTAL_NOTE = ITEMS.registerItem(
            "crystal_note",
            CrystalNoteItem::new,
            new Item.Properties().stacksTo(1).fireResistant()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.crystalmod.main"))
                    .icon(() -> new ItemStack(CRYSTAL_NOTE.get()))
                    .displayItems((params, output) -> {
                        output.accept(CRYSTAL_NOTE.get());
                        output.accept(ATM_ITEM.get());
                        output.accept(MARKET_STALL_ITEM.get());
                        output.accept(MARKET_TERMINAL_ITEM.get());
                    })
                    .build()
    );

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        DATA_COMPONENTS.register(modBus);
    }
}
