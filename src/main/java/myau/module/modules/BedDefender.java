package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BedDefender extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty buildSpeed = new IntProperty("build-speed", 80, 30, 300);
    public final IntProperty jitter = new IntProperty("jitter", 0, 0, 100);
    public final FloatProperty range = new FloatProperty("range", 4.5f, 1.0f, 8.0f);
    public final IntProperty pyramidSize = new IntProperty("pyramid-size", 3, 2, 5);
    public final BooleanProperty autoDisable = new BooleanProperty("auto-disable", true);
    public final BooleanProperty renderPreview = new BooleanProperty("render-preview", true);

    private static final int ROT_PRIORITY = 6;
    private static final int BED_RADIUS = 8;
    private static final int MAX_ATTEMPTS = 20;
    private static final int MAX_VERIFICATION_PASSES = 3;
    private static final long CONFIRM_TIMEOUT_NANOS = 500_000_000L;

    private final Block[] layerMaterials = {
            Blocks.end_stone,
            Blocks.glass,
            Blocks.wool
    };

    private BlockPos bedFoot;
    private BlockPos bedHead;
    private EnumFacing bedFacing;

    private final List<BlockPos> remainingBlocks = new ArrayList<>();
    private final List<BlockPos> pyramidBlueprint = new ArrayList<>();
    private final Map<BlockPos, Long> pendingConfirmation = new HashMap<>();
    private final Set<BlockPos> placedBlocks = new HashSet<>();
    private final Map<BlockPos, Integer> attemptCounts = new HashMap<>();

    private long lastPlaceTime;
    private long currentCooldownNanos;
    private boolean isBuilding;
    private Block lastSelectedMaterial;
    private int verificationPasses;

    private BlockPos currentTarget;
    private EnumFacing currentTargetFacing;
    private Vec3 currentTargetHitVec;
    private BlockPos currentPlacePos;
    private boolean hasTarget;

    public BedDefender() {
        super("BedDefender", false);
    }

    @Override
    public void onEnabled() {
        resetState();
        findBed();
        if (bedFoot != null) {
            generatePyramid();
        } else {
            setEnabled(false);
        }
    }

    @Override
    public void onDisabled() {
        resetState();
    }

    private void resetState() {
        bedFoot = null;
        bedHead = null;
        bedFacing = null;
        remainingBlocks.clear();
        pyramidBlueprint.clear();
        pendingConfirmation.clear();
        placedBlocks.clear();
        attemptCounts.clear();
        lastSelectedMaterial = null;
        verificationPasses = 0;
        isBuilding = false;
        lastPlaceTime = 0L;
        currentCooldownNanos = 0L;
        currentTarget = null;
        currentTargetFacing = null;
        currentTargetHitVec = null;
        currentPlacePos = null;
        hasTarget = false;
    }

    private void findBed() {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        BlockPos origin = mc.thePlayer.getPosition();

        for (int dx = -BED_RADIUS; dx <= BED_RADIUS; dx++) {
            for (int dy = -BED_RADIUS; dy <= BED_RADIUS; dy++) {
                for (int dz = -BED_RADIUS; dz <= BED_RADIUS; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    Block block = state.getBlock();
                    if (!(block instanceof BlockBed)) continue;

                    BlockBed.EnumPartType part = state.getValue(BlockBed.PART);
                    EnumFacing facing = state.getValue(BlockBed.FACING);

                    BlockPos foot = part == BlockBed.EnumPartType.HEAD
                            ? pos.offset(facing.getOpposite())
                            : pos;

                    IBlockState footState = mc.theWorld.getBlockState(foot);
                    if (footState.getBlock() instanceof BlockBed
                            && footState.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                        bedFoot = foot;
                        bedFacing = footState.getValue(BlockBed.FACING);
                        bedHead = bedFoot.offset(bedFacing);
                        return;
                    }
                }
            }
        }
    }

    private void generatePyramid() {
        remainingBlocks.clear();
        pyramidBlueprint.clear();
        pendingConfirmation.clear();
        attemptCounts.clear();
        verificationPasses = 0;
        if (bedFoot == null || bedFacing == null) return;

        int size = pyramidSize.getValue();
        int dirX = bedFacing.getFrontOffsetX();
        int dirZ = bedFacing.getFrontOffsetZ();

        List<BlockPos> allPositions = new ArrayList<>();

        for (int layer = 0; layer < size; layer++) {
            int halfSize = size - layer - 1;
            int yOffset = layer;

            for (int x = -halfSize; x <= halfSize; x++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    boolean isBedFoot = x == 0 && z == 0;
                    boolean isBedHead = x == dirX && z == dirZ;
                    if ((isBedFoot || isBedHead) && yOffset == 0) continue;

                    if (Math.abs(x) + Math.abs(z) > halfSize) continue;

                    BlockPos pos = new BlockPos(
                            bedFoot.getX() + x,
                            bedFoot.getY() + yOffset,
                            bedFoot.getZ() + z
                    );

                    Block existing = mc.theWorld.getBlockState(pos).getBlock();
                    if (existing != Blocks.air && !(existing instanceof BlockBed)) continue;
                    if (isGroundBlock(pos)) continue;

                    allPositions.add(pos);
                }
            }
        }

        allPositions.sort((a, b) -> {
            if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
            return Double.compare(a.distanceSq(bedFoot), b.distanceSq(bedFoot));
        });

        pyramidBlueprint.addAll(allPositions);
        remainingBlocks.addAll(allPositions);
        isBuilding = !remainingBlocks.isEmpty();
    }

    private boolean isGroundBlock(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.grass || block == Blocks.dirt || block == Blocks.stone
                || block == Blocks.sand || block == Blocks.gravel || block == Blocks.clay
                || block == Blocks.farmland || block == Blocks.soul_sand || block == Blocks.mycelium;
    }

    private Block getLayerMaterial(int layer) {
        if (layer >= 0 && layer < layerMaterials.length) {
            return layerMaterials[layer];
        }
        return Blocks.wool;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (bedFoot == null || !(mc.theWorld.getBlockState(bedFoot).getBlock() instanceof BlockBed)) {
            bedFoot = null;
            bedHead = null;
            bedFacing = null;
            findBed();
            if (bedFoot != null) generatePyramid();
            return;
        }

        hasTarget = false;
        currentTarget = null;
        currentTargetFacing = null;
        currentTargetHitVec = null;

        if (remainingBlocks.isEmpty()) {
            handleQueueEmpty();
            return;
        }

        long now = System.nanoTime();

        BlockPos placePos = null;
        BlockPos supportPos = null;
        EnumFacing targetFace = null;
        Vec3 targetHit = null;
        float bestYaw = 0;
        float bestPitch = 0;

        Iterator<BlockPos> it = remainingBlocks.iterator();
        while (it.hasNext()) {
            BlockPos candidate = it.next();

            Block existing = mc.theWorld.getBlockState(candidate).getBlock();
            if (existing != Blocks.air) {
                it.remove();
                pendingConfirmation.remove(candidate);
                attemptCounts.remove(candidate);
                placedBlocks.add(candidate);
                continue;
            }

            Long sentAt = pendingConfirmation.get(candidate);
            if (sentAt != null) {
                if (now - sentAt < CONFIRM_TIMEOUT_NANOS) continue;
                pendingConfirmation.remove(candidate);
                placedBlocks.remove(candidate);
                if (registerFailedAttempt(candidate)) {
                    it.remove();
                }
                continue;
            }

            PlacementData data = findPlacement(candidate);
            if (data == null) continue;

            float[] rot = computeRotations(data.hitVec);
            if (rot == null) continue;

            placePos = candidate;
            supportPos = data.neighbor;
            targetFace = data.side;
            targetHit = data.hitVec;
            bestYaw = rot[0];
            bestPitch = rot[1];
            break;
        }

        if (placePos == null) return;

        currentPlacePos = placePos;
        currentTarget = supportPos;
        currentTargetFacing = targetFace;
        currentTargetHitVec = targetHit;
        hasTarget = true;

        int layer = placePos.getY() - bedFoot.getY();
        Block material = getLayerMaterial(layer);
        if (material != lastSelectedMaterial) {
            if (selectBlock(material)) {
                lastSelectedMaterial = material;
            } else if (selectBestBlock()) {
                ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                lastSelectedMaterial = (held != null && held.getItem() instanceof ItemBlock)
                        ? ((ItemBlock) held.getItem()).getBlock() : null;
            } else {
                lastSelectedMaterial = null;
            }
        }

        float yawDiff = MathHelper.wrapAngleTo180_float(bestYaw - event.getYaw());
        float pitchDiff = bestPitch - event.getPitch();

        float steppedYaw = event.getYaw() + MathHelper.clamp_float(yawDiff, -180f, 180f);
        float steppedPitch = MathHelper.clamp_float(event.getPitch() + MathHelper.clamp_float(pitchDiff, -180f, 180f), -90f, 90f);

        event.setRotation(steppedYaw, steppedPitch, ROT_PRIORITY);

        if (!hasTarget || currentTarget == null || currentTargetFacing == null || currentTargetHitVec == null)
            return;

        if (now - lastPlaceTime < currentCooldownNanos) return;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;

        mc.thePlayer.setSneaking(true);

        mc.thePlayer.swingItem();
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, currentTarget, currentTargetFacing, currentTargetHitVec);

        mc.thePlayer.setSneaking(false);

        lastPlaceTime = now;
        currentCooldownNanos = nextCooldownNanos();
        pendingConfirmation.put(currentPlacePos, now);
        placedBlocks.add(currentPlacePos);
        hasTarget = false;
        currentTarget = null;
        currentTargetFacing = null;
        currentTargetHitVec = null;
        currentPlacePos = null;
    }

    private void handleQueueEmpty() {
        if (!isBuilding) return;

        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos pos : pyramidBlueprint) {
            if (mc.theWorld.getBlockState(pos).getBlock() == Blocks.air) {
                missing.add(pos);
            }
        }

        if (!missing.isEmpty() && verificationPasses < MAX_VERIFICATION_PASSES) {
            verificationPasses++;
            attemptCounts.clear();
            remainingBlocks.addAll(missing);
            return;
        }

        isBuilding = false;
        if (autoDisable.getValue()) {
            setEnabled(false);
        }
    }

    private PlacementData findPlacement(BlockPos pos) {
        if (mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > range.getValue()) {
            return null;
        }
        if (placedBlocks.contains(pos)) return null;
        if (mc.theWorld.getBlockState(pos).getBlock() != Blocks.air) return null;

        for (EnumFacing side : getFaceOrder(pos)) {
            BlockPos neighbor = pos.offset(side);
            Block neighborBlock = mc.theWorld.getBlockState(neighbor).getBlock();

            boolean solidNeighbor = isValidSupport(neighborBlock, side);
            boolean placedByUs = placedBlocks.contains(neighbor);
            if (!solidNeighbor && !placedByUs) continue;

            if (!isVisible(pos, side, neighbor)) continue;

            Vec3 hitVec = faceHitVec(pos, side);
            EnumFacing clickedSide = side.getOpposite();

            return new PlacementData(neighbor, clickedSide, hitVec, placedByUs && !solidNeighbor);
        }

        return null;
    }

    private boolean isValidSupport(Block block, EnumFacing side) {
        if (block == Blocks.air || block instanceof BlockLiquid) return false;
        if (block instanceof BlockBed) return side == EnumFacing.DOWN;
        return true;
    }

    private EnumFacing[] getFaceOrder(BlockPos pos) {
        Vec3 look = mc.thePlayer.getLookVec();
        EnumFacing[] horizontals = EnumFacing.HORIZONTALS.clone();

        java.util.Arrays.sort(horizontals, (a, b) -> Double.compare(
                look.xCoord * b.getDirectionVec().getX() + look.yCoord * b.getDirectionVec().getY() + look.zCoord * b.getDirectionVec().getZ(),
                look.xCoord * a.getDirectionVec().getX() + look.yCoord * a.getDirectionVec().getY() + look.zCoord * a.getDirectionVec().getZ()
        ));

        return new EnumFacing[]{
                EnumFacing.DOWN,
                horizontals[0], horizontals[1], horizontals[2], horizontals[3],
                EnumFacing.UP
        };
    }

    private boolean isVisible(BlockPos pos, EnumFacing side, BlockPos expectedNeighbor) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double nudge = 0.02;
        Vec3 probe = new Vec3(
                pos.getX() + 0.5 + side.getDirectionVec().getX() * (0.5 + nudge),
                pos.getY() + 0.5 + side.getDirectionVec().getY() * (0.5 + nudge),
                pos.getZ() + 0.5 + side.getDirectionVec().getZ() * (0.5 + nudge)
        );

        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(eye, probe, false, true, false);
        if (result == null || result.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return true;

        BlockPos hit = result.getBlockPos();
        if (hit.equals(expectedNeighbor)) return true;
        if (hit.equals(bedFoot) || hit.equals(bedHead)) return true;
        return placedBlocks.contains(hit);
    }

    private Vec3 faceHitVec(BlockPos pos, EnumFacing side) {
        return new Vec3(
                pos.getX() + 0.5 + side.getDirectionVec().getX() * 0.5,
                pos.getY() + 0.5 + side.getDirectionVec().getY() * 0.5,
                pos.getZ() + 0.5 + side.getDirectionVec().getZ() * 0.5
        );
    }

    private float[] computeRotations(Vec3 target) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double dx = target.xCoord - eye.xCoord;
        double dy = target.yCoord - eye.yCoord;
        double dz = target.zCoord - eye.zCoord;
        double distHoriz = Math.sqrt(dx * dx + dz * dz);

        if (distHoriz < 1.0E-4 && Math.abs(dy) < 1.0E-4) return null;

        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, distHoriz) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    private boolean selectBlock(Block block) {
        if (mc.thePlayer == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock
                    && ((ItemBlock) stack.getItem()).getBlock() == block) {
                mc.thePlayer.inventory.currentItem = i;
                return true;
            }
        }
        return false;
    }

    private boolean selectBestBlock() {
        if (mc.thePlayer == null) return false;
        Block[] priority = {Blocks.end_stone, Blocks.glass, Blocks.wool, Blocks.planks};
        for (Block block : priority) {
            if (selectBlock(block)) return true;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                mc.thePlayer.inventory.currentItem = i;
                return true;
            }
        }
        return false;
    }

    private long nextCooldownNanos() {
        long base = buildSpeed.getValue();
        long extra = jitter.getValue() > 0
                ? ThreadLocalRandom.current().nextLong(0, jitter.getValue() + 1)
                : 0L;
        return (base + extra) * 1_000_000L;
    }

    private boolean registerFailedAttempt(BlockPos pos) {
        int attempts = attemptCounts.getOrDefault(pos, 0) + 1;
        if (attempts >= MAX_ATTEMPTS) {
            attemptCounts.remove(pos);
            return true;
        }
        attemptCounts.put(pos, attempts);
        return false;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled()) return;
        if (!renderPreview.getValue()) return;
        if (!isBuilding || pyramidBlueprint.isEmpty()) return;
        if (mc.theWorld == null) return;

        Block previewBlock = getPreviewBlock();

        RenderUtil.enableRenderState();
        for (BlockPos pos : pyramidBlueprint) {
            if (mc.theWorld.getBlockState(pos).getBlock() != Blocks.air) continue;
            if (placedBlocks.contains(pos)) continue;
            if (pendingConfirmation.containsKey(pos)) continue;

            int color = getBlockColor(previewBlock);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            RenderUtil.drawBlockBox(pos, 1.0, r, g, b);
            RenderUtil.drawBlockBoundingBox(pos, 1.0, r, g, b, 180, 2.0f);
        }
        RenderUtil.disableRenderState();
    }

    private int getBlockColor(Block block) {
        if (block == Blocks.end_stone) return 0xDDDDDD;
        if (block == Blocks.glass) return 0x88CCFF;
        if (block == Blocks.wool) {
            int meta = getWoolMeta();
            switch (meta) {
                case 0:  return 0xFFFFFF;
                case 1:  return 0xFFAA00;
                case 2:  return 0xAA00AA;
                case 3:  return 0x55CCFF;
                case 4:  return 0xFFFF00;
                case 5:  return 0x55FF55;
                case 6:  return 0xFF55FF;
                case 7:  return 0x555555;
                case 8:  return 0x888888;
                case 9:  return 0x55CCCC;
                case 10: return 0xAA55AA;
                case 11: return 0x5555FF;
                case 12: return 0xCC8844;
                case 13: return 0x55AA55;
                case 14: return 0xFF5555;
                case 15: return 0x222222;
                default: return 0xFFFFFF;
            }
        }
        if (block == Blocks.planks) return 0xCCAA66;
        return 0x888888;
    }

    private int getWoolMeta() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemBlock) {
            return held.getItemDamage();
        }
        return 0;
    }

    private Block getPreviewBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemBlock) {
            return ((ItemBlock) held.getItem()).getBlock();
        }
        return Blocks.wool;
    }

    @Override
    public String[] getSuffix() {
        if (!isBuilding) return new String[]{"Ready"};
        return new String[]{remainingBlocks.size() + " blocks"};
    }

    private static class PlacementData {
        final BlockPos neighbor;
        final EnumFacing side;
        final Vec3 hitVec;

        PlacementData(BlockPos neighbor, EnumFacing side, Vec3 hitVec, boolean neighborIsPlacedByUs) {
            this.neighbor = neighbor;
            this.side = side;
            this.hitVec = hitVec;
        }
    }
}
