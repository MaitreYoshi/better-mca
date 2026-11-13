package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.client.BirthDayHolder;
import com.maitreyoshi.mcavie.client.HappinessDisplay;
import com.maitreyoshi.mcavie.client.InteractScreenHolder;
import com.maitreyoshi.mcavie.work.client.WorkClient;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.maitreyoshi.mcavie.client.MCAVieClient;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.social.client.RelationsClient;
import net.mca.client.gui.InteractScreen;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDate;
import java.util.UUID;

@Mixin(InteractScreen.class)
public abstract class InteractScreenMixin extends Screen implements BirthDayHolder, InteractScreenHolder {
    @Unique
    private static final int ROW_HEIGHT = 17;

    @Shadow(remap = false)
    @Final
    private VillagerLike<?> villager;

    @Unique
    private Long mcavie$birthDay;

    @Unique
    private Component mcavie$birthLine;

    @Unique
    private long mcavie$birthLineDay;

    @Unique
    private long mcavie$birthLineToday;

    @Unique
    private HappinessDisplay.Entry mcavie$menuEntry;

    @Unique
    private Component mcavie$menuLine;

    private InteractScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mcavie$requestBirthDay(CallbackInfo ci) {
        MCAVieClient.requestBirthDay(villager.asEntity().getUUID());
        RelationsClient.request(villager.asEntity().getUUID());
    }

    @ModifyConstant(method = "drawTextPopups", constant = @Constant(intValue = 4), remap = false,
            slice = @Slice(
                    from = @At(value = "CONSTANT", args = "stringValue=gui.interact.label.personality"),
                    to = @At(value = "INVOKE", target = "Lnet/mca/client/gui/InteractScreen;hoveringOverIcon(Ljava/lang/String;)Z", ordinal = 0)
            ))
    private int mcavie$moveTraitsDown(int row) {
        return row + mcavie$extraRows();
    }

    @Unique
    private int mcavie$extraRows() {
        return (mcavie$birthDay == null ? 0 : 1) + (mcavie$happiness() == null ? 0 : 1) + (mcavie$bestFriend() == null ? 0 : 1);
    }

    @Unique
    private WorkClient.Info mcavie$job() {
        WorkClient.Info info = WorkClient.get(villager.asEntity().getId());
        return info == null || info.job() == com.maitreyoshi.mcavie.work.Job.NONE ? null : info;
    }

    @Redirect(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/mca/entity/VillagerLike;getProfessionText()Lnet/minecraft/network/chat/MutableComponent;", remap = true))
    private MutableComponent mcavie$showAddonJob(VillagerLike<?> instance) {
        WorkClient.Info job = mcavie$job();
        return job == null ? instance.getProfessionText() : WorkClient.jobLine(job);
    }

    @Override
    public Entity mcavie$getVillagerEntity() {
        return villager.asEntity();
    }

    @Unique
    private RelationsClient.Entry mcavie$bestFriend() {
        return RelationsClient.bestFriend(villager.asEntity().getUUID());
    }

    @Unique
    private HappinessDisplay.Entry mcavie$happiness() {
        return HappinessDisplay.get(villager.asEntity().getId());
    }

    @Inject(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/mca/client/gui/InteractScreen;hoveringOverIcon(Ljava/lang/String;)Z", ordinal = 0))
    private void mcavie$drawBirthDay(GuiGraphics context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        HappinessDisplay.Entry happiness = mcavie$happiness();
        if (happiness != null) {
            int row = 4 + (mcavie$birthDay == null ? 0 : 1);
            if (happiness != mcavie$menuEntry) {
                mcavie$menuEntry = happiness;
                mcavie$menuLine = HappinessDisplay.menuLine(happiness);
            }
            context.renderTooltip(font, mcavie$menuLine, 10, 30 + ROW_HEIGHT * row);
        }

        RelationsClient.Entry friend = mcavie$bestFriend();
        if (friend != null) {
            int row = 4 + (mcavie$birthDay == null ? 0 : 1) + (happiness == null ? 0 : 1);
            Component line = Component.translatable(friend.female() ? "gui.mca_vie.relations.best_friend.female" : "gui.mca_vie.relations.best_friend",
                    Component.literal(friend.name()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.WHITE);
            context.renderTooltip(font, line, 10, 30 + ROW_HEIGHT * row);
        }

        if (mcavie$birthDay == null) {
            return;
        }
        long birth = mcavie$birthDay;
        long today = WorldCalendar.exactCalendarDay(client.level);
        if (mcavie$birthLine == null || mcavie$birthLineDay != birth || mcavie$birthLineToday != today) {
            mcavie$birthLine = mcavie$createBirthLine(birth, today);
            mcavie$birthLineDay = birth;
            mcavie$birthLineToday = today;
        }
        context.renderTooltip(font, mcavie$birthLine, 10, 30 + ROW_HEIGHT * 4);
    }

    @Unique
    private Component mcavie$createBirthLine(long birth, long today) {
        LocalDate date = WorldCalendar.dateOfDay(birth);
        // today = jour exact du calendrier qui défile : l'âge change le jour de l'anniversaire, même en vitesse « 1 an »
        int age = com.maitreyoshi.mcavie.tree.client.TreeTexts.ageBetween(null, birth, today);

        Component day = date.getDayOfMonth() == 1
                ? Component.translatable("calendar.mca_vie.first_day")
                : Component.literal(String.valueOf(date.getDayOfMonth()));
        String key = villager.getGenetics().getGender() == Gender.FEMALE ? "gui.mca_vie.born_female" : "gui.mca_vie.born_male";
        return Component.translatable(key,
                day,
                Component.translatable("calendar.mca_vie.month." + date.getMonthValue()),
                WorldCalendar.displayYearOfDay(birth),
                Component.translatable(age <= 1 ? "gui.mca_vie.age_one" : "gui.mca_vie.age", age)
        ).withStyle(ChatFormatting.WHITE);
    }

    @Override
    public UUID mcavie$getVillagerUuid() {
        return villager.asEntity().getUUID();
    }

    @Override
    public void mcavie$setBirthDay(long birthDay) {
        mcavie$birthDay = birthDay;
    }
}
