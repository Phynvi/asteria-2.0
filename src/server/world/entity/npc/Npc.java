package server.world.entity.npc;

import java.util.List;

import server.core.Rs2Engine;
import server.core.worker.Worker;
import server.world.entity.Animation;
import server.world.entity.Entity;
import server.world.entity.UpdateFlags.Flag;
import server.world.entity.npc.NpcDeathDrop.DeathDrop;
import server.world.entity.player.Player;
import server.world.item.ground.GroundItem;
import server.world.item.ground.StaticGroundItem;
import server.world.map.Position;

/**
 * A non-player-character that extends Entity so that we can share the many
 * similar attributes.
 * 
 * @author blakeman8192
 * @author lare96
 */
public class Npc extends Entity {

    /** The npc ID. */
    private int npcId;

    /** Whether or not the npc is visible. */
    private boolean isVisible = true;

    /** The npcs max health. */
    private int maxHealth;

    /** The npcs current health. */
    private int currentHealth;

    /** If this npc respawns or not. */
    private boolean respawn = true;

    /** The movement coordinator for this npc. */
    private NpcMovementCoordinator movementCoordinator = new NpcMovementCoordinator(this);

    /**
     * The npcs position from the moment of conception. This position never
     * changes.
     */
    private Position originalPosition = new Position();

    /** If this npc was originally random walking. */
    private boolean originalRandomWalk;

    /** The respawn ticks. */
    private int respawnTicks;

    /**
     * Creates a new {@link Npc}.
     * 
     * @param npcId
     *        the npc ID.
     * @param position
     *        the npcs position.
     */
    public Npc(int npcId, Position position) {
        this.npcId = npcId;
        this.getPosition().setAs(position);
        this.originalPosition.setAs(position);
        this.maxHealth = getDefinition().getHitpoints();
        this.setCurrentHealth(getDefinition().getHitpoints());
        this.setAutoRetaliate(true);
        this.getFlags().flag(Flag.APPEARANCE);
    }

    @Override
    public void pulse() throws Exception {
        movementCoordinator.coordinate();
        getMovementQueue().execute();
    }

    @Override
    public Worker death() throws Exception {
        return new Worker(1, false) {

            @Override
            public void fire() {

                /** After two ticks play the death animation for this npc. */
                if (getDeathTicks() == 1) {
                    animation(new Animation(getDefinition().getDeathAnimation()));

                    /** After 7 ticks remove the npc and begin respawning. */
                } else if (getDeathTicks() == 6) {

                    /** Drop the items on death and remove the npc from the area. */
                    if (respawnTicks == 0) {
                        Entity killer = getCombatSession().getLastHitBy();
                        // TODO: ^^ proper death calculations
                        dropDeathItems(killer);
                        move(new Position(1, 1));

                        if (!isRespawn()) {
                            this.cancel();
                        }
                    }

                    /** Respawn the npc when a set amount of time has elapsed. */
                    if (respawnTicks == getRespawnTime()) {
                        getPosition().setAs(getOriginalPosition());
                        register();
                        this.cancel();
                    } else {
                        respawnTicks++;
                    }
                    return;
                }

                incrementDeathTicks();
            }
        };
    }

    @Override
    public void move(Position position) {
        getMovementQueue().reset();
        getPosition().setAs(position);
        getFlags().flag(Flag.APPEARANCE);
        unregister();
    }

    @Override
    public void register() {
        for (int i = 1; i < Rs2Engine.getWorld().getNpcs().length; i++) {
            if (Rs2Engine.getWorld().getNpcs()[i] == null) {
                Rs2Engine.getWorld().getNpcs()[i] = this;
                this.setSlot(i);
                return;
            }
        }
        throw new IllegalStateException("Server is full!");
    }

    @Override
    public void unregister() {
        if (this.getSlot() == -1) {
            return;
        }

        Rs2Engine.getWorld().getNpcs()[this.getSlot()] = null;
        this.setUnregistered(true);
    }

    @Override
    public void follow(final Entity entity) {

    }

    @Override
    public String toString() {
        return "NPC(" + getSlot() + ":" + getDefinition().getName() + ")";
    }

    /**
     * Drops items for the entity that killed this npc.
     * 
     * @param killer
     *        the killer for this entity (if any).
     * @param global
     *        if this drop should be static.
     */
    public void dropDeathItems(Entity killer) {

        /** Get the drops for this npc. */
        List<DeathDrop> dropItems = NpcDeathDrop.calculateDeathDrop(this);

        /** Block if there are no drops. */
        if (dropItems.size() == 0) {
            return;
        }

        /**
         * If the killer is an npc register static ground items that will vanish
         * within a minute.
         */
        if (killer == null || killer instanceof Npc) {
            for (DeathDrop drop : dropItems) {
                GroundItem.getRegisterable().register(new StaticGroundItem(drop.getItem(), getPosition(), true, false));
            }

            /**
             * If the killer is a player register normal ground items just for
             * the killer.
             */
        } else if (killer instanceof Player) {
            Player player = (Player) killer;

            for (DeathDrop drop : dropItems) {
                GroundItem.getRegisterable().register(new GroundItem(drop.getItem(), new Position(getPosition().getX(), getPosition().getY(), getPosition().getZ()), player));
            }
        }
    }

    /**
     * Gets the respawn time in ticks.
     * 
     * @return the respawn time in ticks.
     */
    public int getRespawnTime() {
        return (getDefinition().getRespawnTime() == 0 ? 1 : getDefinition().getRespawnTime()) * 2;
    }

    /**
     * Increases this npcs health.
     * 
     * @param amount
     *        the amount to increase by.
     */
    public void increaseHealth(int amount) {
        if ((currentHealth + amount) > maxHealth) {
            currentHealth = maxHealth;
            return;
        }

        currentHealth += amount;
    }

    /**
     * Decreases this npcs health.
     * 
     * @param amount
     *        the amount to decrease by.
     */
    public void decreaseHealth(int amount) {
        if ((currentHealth - amount) < 0) {
            currentHealth = 0;
            return;
        }

        currentHealth -= amount;
    }

    /**
     * Gets the npc id.
     * 
     * @return the npc id.
     */
    public int getNpcId() {
        return npcId;
    }

    /**
     * Gets if this npc is visible or not.
     * 
     * @return true if this npc is visible.
     */
    public boolean isVisible() {
        return isVisible;
    }

    /**
     * Set this npc's visibility.
     * 
     * @param isVisible
     *        if this npc should be visible or invisible.
     */
    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

    /**
     * Gets the max health of this npc.
     * 
     * @return the max health.
     */
    public int getMaxHealth() {
        return maxHealth;
    }

    /**
     * Gets this npc's current health.
     * 
     * @return the current health.
     */
    public int getCurrentHealth() {
        return currentHealth;
    }

    /**
     * Sets this npc's current health.
     * 
     * @param currentHealth
     *        the new health value to set.
     */
    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    /**
     * Gets the original position of this npc (from the moment of conception).
     * 
     * @return the original position.
     */
    public Position getOriginalPosition() {
        return originalPosition;
    }

    /**
     * Gets a npc definition.
     * 
     * @param id
     *        the npc definition to get.
     * @return the definition.
     */
    public NpcDefinition getDefinition() {
        return NpcDefinition.getNpcDefinition()[npcId];
    }

    /**
     * Gets if this npc was originally walking.
     * 
     * @return the original random walk.
     */
    public boolean isOriginalRandomWalk() {
        return originalRandomWalk;
    }

    /**
     * Sets if this npc was originally walking.
     * 
     * @param originalRandomWalk
     *        the original random walk to set.
     */
    public void setOriginalRandomWalk(boolean originalRandomWalk) {
        this.originalRandomWalk = originalRandomWalk;
    }

    /**
     * Sets if this npc should respawn on death.
     * 
     * @param respawn
     *        the respawn to set.
     */
    public void setRespawn(boolean respawn) {
        this.respawn = respawn;
    }

    /**
     * Gets if this npc will respawn on death.
     * 
     * @return the respawn.
     */
    public boolean isRespawn() {
        return respawn;
    }

    /**
     * Get the movement coordinator.
     * 
     * @return the movement coordinator.
     */
    public NpcMovementCoordinator getMovementCoordinator() {
        return movementCoordinator;
    }
}
