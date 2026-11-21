package cn.tolim.pudans.infra.api;

import com.github.zafarkhaja.semver.Version;
import java.util.function.Supplier;


public interface SystemVersionSupplier extends Supplier<Version> {}
