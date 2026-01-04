package com.gutou.resource;

import com.gutou.hk2.Hk2ServiceUtils;
import com.gutou.model.entity.User;
import com.gutou.service.user.TaskMngApi;
import com.gutou.service.user.UserServiceApi;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private static final Logger logger = LoggerFactory.getLogger(UserResource.class);

    @Inject
    private UserServiceApi userService;

    @Inject
    private TaskMngApi taskMngApi;

    @GET
    @UnitOfWork
    public List<User> getAllUsers() {
        logger.debug("获取所有用户列表");
        return userService.findAll();
    }

    @GET
    @Path("/{id}")
    @UnitOfWork
    public User getUserById(@PathParam("id") Long id) {
        return userService.findById(id)
                .orElseThrow(() -> new RuntimeException("user not find by id:" + id));
    }

    @POST
    @UnitOfWork
    public Response createUser(User user) {
        logger.info("创建用户请求: username={}, email={}", user.getUsername(), user.getEmail());
        try {
            User created = userService.create(user);
            logger.info("用户创建成功: id={}, username={}", created.getId(), created.getUsername());
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            logger.warn("用户创建失败: {}", e.getMessage());
            return Response.status(Response.Status.CONFLICT)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    @UnitOfWork
    public Response updateUser(@PathParam("id") Long id, User user) {
        return userService.findById(id)
                .map(existing -> {
                    existing.setUsername(user.getUsername());
                    existing.setEmail(user.getEmail());
                    existing.setFullName(user.getFullName());
                    User updated = userService.update(existing);
                    return Response.ok(updated).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @UnitOfWork
    public Response deleteUser(@PathParam("id") Long id) {
        return userService.findById(id)
                .map(user -> {
                    userService.deleteById(id);
                    return Response.noContent().build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/email/{email}")
    @UnitOfWork
    public Response getUserByEmail(@PathParam("email") String email) {
        return userService.findByEmail(email)
                .map(user -> Response.ok(user).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/task")
    @UnitOfWork
    public User getTaskInfo() {
        List<Long> list = userService.findAll().stream()
                .map(User::getId)
                .toList();
        logger.info("all ids is:{}", list);

        TaskMngApi service = Hk2ServiceUtils.getRequiredService(TaskMngApi.class);
        if (service != null) {
            service.execute();
        }
        Hk2ServiceUtils.logAllServiceDescriptors();

        taskMngApi.execute();

        return userService.findById(list.getFirst()).orElseThrow(RuntimeException::new);
    }
}

