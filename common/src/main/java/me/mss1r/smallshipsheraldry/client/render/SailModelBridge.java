package me.mss1r.smallshipsheraldry.client.render;

import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class SailModelBridge {
    private SailModelBridge() {
    }

    static void setupAnim(SailModel sailModel, Ship ship, float limbSwing) {
        EntityModel<Ship> entityModel = sailModel;
        entityModel.setupAnim(ship, limbSwing, 0.0F, -0.1F, 0.0F, 0.0F);
    }

    static RenderType renderType(SailModel sailModel, ResourceLocation texture) {
        EntityModel<Ship> entityModel = sailModel;
        return entityModel.renderType(texture);
    }
}
