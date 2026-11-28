
package cn.tolim.pudans.iam.protocol.jwt.token;


@FunctionalInterface
public interface IdTokenGenerator {

    IdToken generate(IdTokenContext context);

}
