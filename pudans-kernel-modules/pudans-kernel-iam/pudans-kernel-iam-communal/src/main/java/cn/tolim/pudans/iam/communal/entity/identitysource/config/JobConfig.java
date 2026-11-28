package cn.tolim.pudans.iam.communal.entity.identitysource.config;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import com.alibaba.fastjson2.annotation.JSONField;
import com.cronutils.builder.CronBuilder;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.field.expression.Every;
import com.cronutils.model.field.expression.FieldExpression;
import com.cronutils.model.field.value.IntegerFieldValue;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import static com.cronutils.model.field.expression.FieldExpressionFactory.*;


@Slf4j
@Data
@Schema(description = "任务配置")
@AllArgsConstructor
@NoArgsConstructor
public class JobConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = -1L;

    // Mode
    @Parameter(description = "任务执行模式")
    @NotNull(message = "请选择任务执行模式")
    private Mode mode;

    // Task execution day of week
    @NotNull(message = "请选择任务执行星期值")
    @Parameter(description = "任务执行星期值")
    private List<DayOfWeek> dayOfWeek;

    // Value
    @Parameter(description = "任务执行值")
    @NotEmpty(message = "任务执行值不能为空")
    @JsonAlias({"time", "interval"})
    private String value;

    // Get expression
    // Returns: String
    @JSONField(serialize = false, deserialize = false)
    @JsonIgnore
    public String getCronExpression(CronType cronType) {
        if (!(cronType.equals(CronType.SPRING) || cronType.equals(CronType.QUARTZ))) {
            throw new PudansException("不支持该类型 [" + cronType + "]");
        }
        // Hour
        FieldExpression hour = always();
        // Minute
        FieldExpression minute = always();
        // Second
        FieldExpression second = always();
        // Process day of week
        FieldExpression dayOfWeek = null;
        if (this.dayOfWeek.contains(DayOfWeek.always)) {
            dayOfWeek = DayOfWeek.always.getCode();
        } else {
            for (DayOfWeek week : this.dayOfWeek) {
                if (Objects.isNull(dayOfWeek)) {
                    dayOfWeek = week.getCode();
                    continue;
                }
                dayOfWeek = dayOfWeek.and(week.getCode());
            }
        }
        // Parse hour, minute, and second for scheduled mode
        if (mode.equals(JobConfig.Mode.timed)) {
            LocalTime time = LocalTime.parse(value, DateTimeFormatter.ofPattern("H[H]:mm:ss"));
            hour = on(time.getHour());
            minute = on(time.getMinute());
            second = on(time.getSecond());
        }
        // Execute recurring mode (0 through the specified hour)
        if (mode.equals(JobConfig.Mode.period)) {
            hour = new Every(on(0), new IntegerFieldValue(Integer.parseInt(value)));
            minute = on(0);
            second = on(0);
        }
        // Java(Spring) cron format: second, minute, hour, day of month, month, day of week, and optional year.
        // Day of week supports 1-7 or SUN-SAT, and month supports 1-12 or JAN-DEC.
        CronBuilder cronBuilder = CronBuilder
                .cron(CronDefinitionBuilder.instanceDefinitionFor(cronType))
                // Second
                .withSecond(second)
                // Minute
                .withMinute(minute)
                // Hour
                .withHour(hour)
                // Day
                .withDoM(questionMark())
                // Month
                .withMonth(always())
                // Day of week
                .withDoW(dayOfWeek);
        Cron cron = cronBuilder.instance();
        // Obtain the string expression
        String cronAsString = cron.asString();
        // SPRING
        CronExpression parse = CronExpression.parse(cronAsString);
        log.debug("Spring Cron 表达式：{} ", parse);
        return parse.toString();
    }

    public enum Mode {
        // Recurring
        period,
        // Scheduled
        timed
    }

    // Day of week
    public enum DayOfWeek {
        // Always
        always(between(1, 7)),
        // Monday
        monday(on(1)),
        // Tuesday
        tuesday(on(2)),
        // Wednesday
        wednesday(on(3)),
        // Thursday
        thursday(on(4)),
        // Friday
        friday(on(5)),
        // Saturday
        saturday(on(6)),
        // Sunday
        sunday(on(7));

        private final FieldExpression code;

        DayOfWeek(FieldExpression code) {
            this.code = code;
        }

        public FieldExpression getCode() {
            return code;
        }
    }
}
