package com.github.technus.tectech.recipe;

import static com.google.common.math.LongMath.pow;
import static gregtech.api.util.GT_Utility.getPlasmaFuelValueInEUPerLiterFromMaterial;

import com.github.technus.tectech.util.ItemStackLong;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import gnu.trove.map.TMap;
import gnu.trove.map.hash.TCustomHashMap;
import gnu.trove.strategy.HashingStrategy;
import gregtech.api.enums.Materials;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.tuple.Pair;
import pers.gwyog.gtneioreplugin.util.GT5OreLayerHelper;
import pers.gwyog.gtneioreplugin.util.GT5OreSmallHelper;

public class EyeOfHarmonyRecipe {

    HashingStrategy<ItemStack> itemStackHashingStrategy = new HashingStrategy<ItemStack>() {
        @Override
        public int computeHashCode(ItemStack stack) {
            // Not really sure how this works or if it is "unique enough".
            int result = stack.getItem().hashCode();
            result = 31 * result + stack.getItemDamage();
            return result;
        }

        @Override
        public boolean equals(ItemStack item1, ItemStack item2) {
            return item1.getUnlocalizedName().equals(item2.getUnlocalizedName());
        }
    };

    private final TMap<ItemStack, Double> itemStackToProbabilityMap = new TCustomHashMap<>(itemStackHashingStrategy);
    private final TMap<ItemStack, Long> itemStackToTrueStackSizeMap = new TCustomHashMap<>(itemStackHashingStrategy);

    private final List<ItemStackLong> outputItems;
    private final FluidStack[] outputFluids;

    private final long hydrogenRequirement;
    private final long heliumRequirement;

    private final long euOutput;
    private final long euStartCost;

    private final double baseSuccessChance;

    private final long spacetimeCasingTierRequired;

    private final long miningTimeSeconds;

    private final ItemStack recipeTriggerItem;

    public TMap<ItemStack, Double> getItemStackToProbabilityMap() {
        return itemStackToProbabilityMap;
    }

    public TMap<ItemStack, Long> getItemStackToTrueStackSizeMap() {
        return itemStackToTrueStackSizeMap;
    }

    public EyeOfHarmonyRecipe(
            GT5OreLayerHelper.NormalOreDimensionWrapper normalOreDimensionWrapper,
            GT5OreSmallHelper.SmallOreDimensionWrapper smallOreDimensionWrapper,
            double recipeEnergyEfficiency, // E.g. 90% efficient = 0.9 = lose 10% EU from plasma + EU output.
            long hydrogenRequirement,
            long heliumRequirement,
            long miningTimeSeconds,
            long spacetimeCasingTierRequired,
            Block block,
            long euOutput,
            double baseSuccessChance) {

        recipeTriggerItem = new ItemStack(block);

        // 6 * 64 = 6 stacks/second for VM tier 3 + Og gas.
        ArrayList<Pair<Materials, Long>> materialList =
                processDimension(normalOreDimensionWrapper, smallOreDimensionWrapper, miningTimeSeconds, 6 * 64);

        this.outputItems = validDustGenerator(materialList);
        this.outputItems.sort(Comparator.comparingLong(ItemStackLong::getStackSize));
        Collections.reverse(this.outputItems);

        long sumOfItems = this.outputItems.stream()
                .map(ItemStackLong::getStackSize)
                .reduce(0L, Long::sum);

        for (ItemStackLong itemStackLong : outputItems) {
            double stackSize = (double) itemStackLong.getStackSize();
            double probability = Math.round(100_000 * stackSize / sumOfItems) / 1000.0;

            itemStackToProbabilityMap.put(itemStackLong.itemStack, probability);
            itemStackToTrueStackSizeMap.put(itemStackLong.itemStack, itemStackLong.stackSize);
        }

        // --- Output fluids and sort them.
        ArrayList<FluidStack> tmpFluidOutputs = validPlasmaGenerator(materialList, 0.1);
        tmpFluidOutputs.sort(Comparator.comparingLong((FluidStack fluid) -> fluid.amount));
        Collections.reverse(tmpFluidOutputs);

        outputFluids = tmpFluidOutputs.toArray(new FluidStack[0]);
        // End.

        this.spacetimeCasingTierRequired = spacetimeCasingTierRequired;

        // 20 ticks, 2^19 (1A UV) eu/t, mining
        long euOfVMRunning = miningTimeSeconds * 20 * pow(2, 19);
        long euValueOfPlasmas = plasmaCostCalculator(this.outputFluids);

        this.euStartCost = euOfVMRunning + euValueOfPlasmas;
        this.euOutput = euOutput;

        this.hydrogenRequirement = hydrogenRequirement;
        this.heliumRequirement = heliumRequirement;

        this.baseSuccessChance = baseSuccessChance;

        this.miningTimeSeconds = miningTimeSeconds;
    }

    // Return clone of list. Deep copy. Maybe a better way to do this?
    public ArrayList<ItemStackLong> getOutputItems() {
        ArrayList<ItemStackLong> copyOutputList = new ArrayList<>();
        for (ItemStackLong itemStackLong : outputItems) {
            copyOutputList.add(new ItemStackLong(itemStackLong));
        }

        return copyOutputList;
    }

    public FluidStack[] getOutputFluids() {
        ArrayList<FluidStack> copyOutputList = new ArrayList<>();

        for (FluidStack fluidStack : outputFluids) {
            copyOutputList.add(fluidStack.copy());
        }

        return copyOutputList.toArray(new FluidStack[0]);
    }

    public long getHydrogenRequirement() {
        return hydrogenRequirement;
    }

    public long getHeliumRequirement() {
        return heliumRequirement;
    }

    public long getEUOutput() {
        return euOutput;
    }

    public long getEUStartCost() {
        return euStartCost;
    }

    public long getRecipeTimeInTicks() {
        return miningTimeSeconds * 20;
    }

    public double getBaseRecipeSuccessChance() {
        return baseSuccessChance;
    }

    public long getSpacetimeCasingTierRequired() {
        return spacetimeCasingTierRequired;
    }

    public ItemStack getRecipeTriggerItem() {
        return recipeTriggerItem;
    }

    static final double primaryMultiplier = (0.1 + 1.0 / 9.0); // Byproduct from macerating/washing chance.
    static final double secondaryMultiplier = (1.0 / 9.0); // Thermal centrifuge byproduct chance.
    static final double tertiaryMultiplier = (0.1); // Macerating thermal centrifuged byproduct chance.
    static final double quaternaryMultiplier = (0.7); // Mercury/chem bath processing chance.

    static final double[] oreMultiplier = {
        primaryMultiplier, secondaryMultiplier, tertiaryMultiplier, quaternaryMultiplier
    };

    private static class HashMapHelper extends HashMap<Materials, Double> {

        void add(Materials material, double value) {

            // If key already exists.
            if (this.containsKey(material)) {
                this.put(material, value + this.get(material));
                return;
            }

            // Otherwise, add value to hashmap entry.
            this.put(material, value);
        }
    }

    static void processHelper(HashMapHelper outputMap, Materials material, double mainMultiplier, double probability) {
        outputMap.add(material.mDirectSmelting, (material.mOreMultiplier * 2) * mainMultiplier * probability);

        int index = 0;
        for (Materials byProductMaterial : material.mOreByProducts) {
            outputMap.add(
                    byProductMaterial.mDirectSmelting, mainMultiplier * (oreMultiplier[index++] * 2) * probability);
        }
    }

    static ArrayList<Pair<Materials, Long>> processDimension(
            GT5OreLayerHelper.NormalOreDimensionWrapper normalOreDimWrapper,
            GT5OreSmallHelper.SmallOreDimensionWrapper smallOreDimWrapper,
            long timeInSeconds,
            long miningMultiplier) {
        HashMapHelper outputMap = new HashMapHelper();

        double mainMultiplier = timeInSeconds * miningMultiplier;

        normalOreDimWrapper.oreVeinToProbabilityInDimension.forEach((veinInfo, probability) -> {
            processHelper(outputMap, veinInfo.mPrimaryVeinMaterial, mainMultiplier, probability);
            processHelper(outputMap, veinInfo.mSecondaryMaterial, mainMultiplier, probability);
            // 8.0 to replicate void miner getDropsVanillaVeins method yields.
            processHelper(outputMap, veinInfo.mBetweenMaterial, mainMultiplier / 8.0, probability);
            processHelper(outputMap, veinInfo.mSporadicMaterial, mainMultiplier / 8.0, probability);
        });

        // Iterate over small ores in dimension and add them, kinda hacky but works and is close enough.
        smallOreDimWrapper.oreVeinToProbabilityInDimension.forEach((veinInfo, probability) -> {
            processHelper(outputMap, veinInfo.getOreMaterial(), mainMultiplier, probability);
        });

        ArrayList<Pair<Materials, Long>> outputList = new ArrayList<>();

        outputMap.forEach((material, quantity) -> outputList.add(Pair.of(material, (long) Math.floor(quantity))));

        return outputList;
    }

    static ArrayList<FluidStack> validPlasmaGenerator(
            final List<Pair<Materials, Long>> planetList, final double percentageOfPlasma) {

        ArrayList<FluidStack> plasmaList = new ArrayList<>();

        for (Pair<Materials, Long> pair : planetList) {
            if (validPlasmas.contains(pair.getLeft())) {
                plasmaList.add(pair.getLeft().getPlasma((int) (pair.getRight() * percentageOfPlasma)));
            }
        }
        return plasmaList;
    }

    static ArrayList<ItemStackLong> validDustGenerator(final ArrayList<Pair<Materials, Long>> planetList) {

        ArrayList<ItemStackLong> dustList = new ArrayList<>();

        for (Pair<Materials, Long> pair : planetList) {
            ItemStack dust = pair.getLeft().getDust(1);
            if (dust != null) {
                dustList.add(new ItemStackLong(dust, pair.getRight()));
            }
        }
        return dustList;
    }

    static long plasmaCostCalculator(FluidStack[] plasmas) {
        long total = 0;

        for (FluidStack plasma : plasmas) {
            total += (plasmaEnergyMap.get(plasma.getFluid()) * plasma.amount);
        }

        return (long) (total * getMaxPlasmaTurbineEfficiency());
    }

    public static double getMaxPlasmaTurbineEfficiency() {
        // I hate Shirabon.
//        return getMaxPlasmaTurbineEfficiency();
        return 3.85;
    }

    static final List<Materials> validPlasmas = Stream.of(
                    Materials.Helium,
                    Materials.Boron,
                    Materials.Nitrogen,
                    Materials.Oxygen,
                    Materials.Sulfur,
                    Materials.Calcium,
                    Materials.Titanium,
                    Materials.Iron,
                    Materials.Nickel,
                    Materials.Zinc,
                    Materials.Silver,
                    Materials.Tin,
                    Materials.Bismuth,
                    Materials.Americium,
                    Materials.Niobium)
            .collect(Collectors.toList());

    static HashMap<Fluid, Long> plasmaEnergyMap = new HashMap<Fluid, Long>() {
        {
            validPlasmas.forEach((material -> put(
                    material.getPlasma(1).getFluid(), (long) getPlasmaFuelValueInEUPerLiterFromMaterial(material))));
        }
    };
}
