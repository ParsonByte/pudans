package cn.tolim.pudans.contract.infra;

import com.github.zafarkhaja.semver.Version;
import java.util.function.Supplier;


public interface SystemVersionSupplier extends Supplier<Version> {}
