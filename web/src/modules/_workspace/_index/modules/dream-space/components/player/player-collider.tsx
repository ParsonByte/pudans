"use client";

import { CapsuleCollider } from "@react-three/rapier";

import { PLAYER_HEIGHT, PLAYER_RADIUS } from "../../constants";

export function PlayerCollider() {
    return <CapsuleCollider args={[PLAYER_HEIGHT / 2 - PLAYER_RADIUS, PLAYER_RADIUS]} friction={0.2} restitution={0} />;
}
