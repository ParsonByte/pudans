import * as THREE from "three";

export const cameraRuntime = {
    collisionDistance: 4,
    desiredPosition: new THREE.Vector3(0, 3, 7),
    focusPoint: null as THREE.Vector3 | null,
    lookTarget: new THREE.Vector3(0, 1.5, 0),
    shake: 0,
};
