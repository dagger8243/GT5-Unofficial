package GoodGenerator.Items;

import gregtech.api.util.GT_Utility;
import ic2.core.IC2Potion;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class RadioactiveItem extends MyItems{

    private final int mRadio;

    public RadioactiveItem(String name, CreativeTabs Tab, int Rad) {
        super(name, Tab);
        this.mRadio = Rad;
    }

    @Override
    public void onUpdate(ItemStack aStack, World aWorld, Entity aPlayer, int aTimer, boolean aIsInHand) {
        super.onUpdate(aStack, aWorld, aPlayer, aTimer, aIsInHand);
        EntityLivingBase tPlayer = (EntityPlayer) aPlayer;
        if (!GT_Utility.isWearingFullRadioHazmat(tPlayer))
            tPlayer.addPotionEffect(new PotionEffect(IC2Potion.radiation.id, mRadio, 4));
    }

}
