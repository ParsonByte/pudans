"use client";

import type { WaterPlan } from "../../types";
import { DreamLake } from "./dream-lake";
import { Fountain } from "./fountain";
import { MistPool } from "./mist-pool";
import { ReflectiveWater } from "./reflective-water";
import { RiverStream } from "./river-stream";
import { WaterCaustics } from "./water-caustics";
import { WaterRipple } from "./water-ripple";
import { Waterfall } from "./waterfall";

export function WaterLayer({ plans, regionId }: { plans: WaterPlan[]; regionId: string }) {
    return (
        <group name={`water-${regionId}`}>
            {plans.map((plan, index) => (
                <group key={plan.id}>
                    {plan.kind === "lake" ? <DreamLake plan={plan} /> : null}
                    {plan.kind === "fountain" ? <Fountain plan={plan} /> : null}
                    {plan.kind === "river" ? <RiverStream plan={plan} /> : null}
                    {plan.kind === "waterfall" ? <Waterfall plan={plan} /> : null}
                    {index === 0 ? (
                        <>
                            <ReflectiveWater plan={plan} />
                            <WaterRipple plan={plan} />
                            <WaterCaustics plan={plan} />
                        </>
                    ) : (
                        <MistPool plan={plan} />
                    )}
                </group>
            ))}
        </group>
    );
}
