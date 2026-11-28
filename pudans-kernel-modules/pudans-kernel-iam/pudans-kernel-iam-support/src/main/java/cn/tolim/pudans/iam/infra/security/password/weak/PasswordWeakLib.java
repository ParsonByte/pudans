package cn.tolim.pudans.iam.infra.security.password.weak;

import java.util.List;

public interface PasswordWeakLib {

    List<String> getWordList();

    Boolean wordExists(String word);
}
