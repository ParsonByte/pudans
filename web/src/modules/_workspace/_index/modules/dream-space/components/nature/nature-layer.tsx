"use client";

import type { NaturePlan } from "../../types";
import { ButterflyFlock } from "./butterfly-flock";
import { CrystalFormation } from "./crystal-formation";
import { FireflySwarm } from "./firefly-swarm";
import { FloatingPollen } from "./floating-pollen";
import { FlowerField } from "./flower-field";
import { GlowingGrass } from "./glowing-grass";
import { MushroomPatch } from "./mushroom-patch";
import { RockCluster } from "./rock-cluster";
import { TreeCluster } from "./tree-cluster";
import { VineCurtain } from "./vine-curtain";

export function NatureLayer({ plans, regionId }: { plans: NaturePlan[]; regionId: string }) {
    return (
        <group name={`nature-${regionId}`}>
            {plans.map((plan, index) => {
                if (plan.kind === "tree") {
                    return <TreeCluster key={plan.id} plan={plan} />;
                }

                if (plan.kind === "grass") {
                    return <GlowingGrass key={plan.id} plan={plan} />;
                }

                if (plan.kind === "flower") {
                    return <FlowerField key={plan.id} plan={plan} />;
                }

                if (plan.kind === "mushroom") {
                    return <MushroomPatch key={plan.id} plan={plan} />;
                }

                if (plan.kind === "rock") {
                    return <RockCluster key={plan.id} plan={plan} />;
                }

                if (plan.kind === "crystal") {
                    return <CrystalFormation key={plan.id} plan={plan} />;
                }

                if (plan.kind === "vine") {
                    return <VineCurtain key={plan.id} plan={plan} />;
                }

                return index % 2 === 0 ? (
                    <FloatingPollen key={plan.id} plan={plan} />
                ) : (
                    <FireflySwarm key={plan.id} plan={plan} />
                );
            })}
            {plans.slice(0, 3).map((plan) => (
                <ButterflyFlock key={`butterfly-${plan.id}`} plan={plan} />
            ))}
        </group>
    );
}
