package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProblemFetchMsg<T extends ArisModule> extends ProblemMessage<T> {

    private final int pid;
    private String problemHash;

    public ProblemFetchMsg(int pid, String moduleName) {
        super(moduleName, null, false, Perm.PROBLEM_FETCH);
        this.pid = pid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemFetchMsg() {
        this(-1, null);
    }

    @Nullable
    @Override
    public ErrorType processProblemMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT DISTINCT p.module_name, p.data, p.problem_hash FROM problem p, assignment a, user_class uc WHERE p.id = ? AND a.problem_id = p.id AND a.class_id = uc.class_id AND uc.user_id = ?;")) {
            statement.setInt(1, pid);
            statement.setInt(2, user.uid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next())
                    return ErrorType.NOT_FOUND;
                String moduleName = rs.getString(1);
                ArisModule<T> module = ModuleService.getService().getModule(moduleName);
                if (module == null)
                    return ErrorType.MISSING_MODULE;
                ProblemConverter<T> converter = module.getProblemConverter();
                try (InputStream in = rs.getBinaryStream(2)) {
                    setProblem(converter.loadProblem(in, false));
                }
                problemHash = rs.getString(3);
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.FETCH_PROBLEM;
    }

    public int getPid() {
        return pid;
    }

    public String getProblemHash() {
        return problemHash;
    }
}
