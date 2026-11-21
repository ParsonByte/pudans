package cn.tolim.pudans.infra.contract;

import com.github.zafarkhaja.semver.Version;
import java.util.function.Supplier;


public interface SystemVersionSupplier extends Supplier<Version> {}
