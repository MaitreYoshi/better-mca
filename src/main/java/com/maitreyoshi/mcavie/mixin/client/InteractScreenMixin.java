package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.client.BirthDayHolder;
import com.maitreyoshi.mcavie.client.HappinessDisplay;
import com.maitreyoshi.mcavie.client.InteractScreenHolder;
import com.maitreyoshi.mcavie.work.client.WorkClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.maitreyoshi.mcavie.client.MCAVieClient;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.social.client.RelationsClient;
import net.mca.client.gui.InteractScreen;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
    private Text mcavie$birthLine;

    @Unique
    private long mcavie$birthLineDay;

    @Unique
    private long mcavie$birthLineToday;

    @Unique
    private HappinessDisplay.Entry mcavie$menuEntry;

    @Unique
    private Text mcavie$menuLine;

    private InteractScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mcavie$requestBirthDay(CallbackInfo ci) {
        MCAVieClient.requestBirthDay(villager.asEntity().getUuid());
        RelationsClient.request(villager.asEntity().getUuid());
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
            at = @At(value = "INVOKE", target = "Lnet/mca/entity/VillagerLike;getProfessionText()Lnet/minecraft/text/MutableText;", remap = true))
    private MutableText mcavie$showAddonJob(VillagerLike<?> instance) {
        WorkClient.Info job = mcavie$job();
        return job == null ? instance.getProfessionText() : WorkClient.jobLine(job);
    }

    @Override
    public Entity mcavie$getVillagerEntity() {
        return villager.asEntity();
    }

    @Unique
    private RelationsClient.Entry mcavie$bestFriend() {
        return RelationsClient.bestFriend(villager.asEntity().getUuid());
    }

    @Unique
    private HappinessDisplay.Entry mcavie$happiness() {
        return HappinessDisplay.get(villager.asEntity().getId());
    }

    @Inject(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/mca/client/gui/InteractScreen;hoveringOverIcon(Ljava/lang/String;)Z", ordinal = 0))
    private void mcavie$drawBirthDay(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        HappinessDisplay.Entry happiness = mcavie$happiness();
        if (happiness != null) {
            int row = 4 + (mcavie$birthDay == null ? 0 : 1);
            if (happiness != mcavie$menuEntry) {
                mcavie$menuEntry = happiness;
                mcavie$menuLine = HappinessDisplay.menuLine(happiness);
            }
            context.drawTooltip(textRenderer, mcavie$menuLine, 10, 30 + ROW_HEIGHT * row);
        }

        RelationsClient.Entry friend = mcavie$bestFriend();
        if (friend != null) {
            int row = 4 + (mcavie$birthDay == null ? 0 : 1) + (happiness == null ? 0 : 1);
            Text line = Text.translatable(friend.female() ? "gui.mca_vie.relations.best_friend.female" : "gui.mca_vie.relations.best_friend",
                    Text.literal(friend.name()).formatted(Formatting.GOLD)).formatted(Formatting.WHITE);
            context.drawTooltip(textRenderer, line, 10, 30 + ROW_HEIGHT * row);
        }

        if (mcavie$birthDay == null) {
            return;
        }
        long birth = mcavie$birthDay;
        long today = WorldCalendar.now(client.world).dayIndex();
        if (mcavie$birthLine == null || mcavie$birthLineDay != birth || mcavie$birthLineToday != today) {
            mcavie$birthLine = mcavie$createBirthLine(birth, today);
            mcavie$birthLineDay = birth;
            mcavie$birthLineToday = today;
        }
        context.drawTooltip(textRenderer, mcavie$birthLine, 10, 30 + ROW_HEIGHT * 4);
    }

    @Unique
    private Text mcavie$createBirthLine(long birth, long today) {
        LocalDate date = WorldCalendar.dateOfDay(birth);
        int age = LifeCycle.ageInYears(birth, today);

        Text day = date.getDayOfMonth() == 1
                ? Text.translatable("calendar.mca_vie.first_day")
                : Text.literal(String.valueOf(date.getDayOfMonth()));
        String key = villager.getGenetics().getGender() == Gender.FEMALE ? "gui.mca_vie.born_female" : "gui.mca_vie.born_male";
        return Text.translatable(key,
                day,
                Text.translatable("calendar.mca_vie.month." + date.getMonthValue()),
                WorldCalendar.displayYearOfDay(birth),
                Text.translatable(age <= 1 ? "gui.mca_vie.age_one" : "gui.mca_vie.age", age)
        ).formatted(Formatting.WHITE);
    }

    @Override
    public UUID mcavie$getVillagerUuid() {
        return villager.asEntity().getUuid();
    }

    @Override
    public void mcavie$setBirthDay(long birthDay) {
        mcavie$birthDay = birthDay;
    }
}
