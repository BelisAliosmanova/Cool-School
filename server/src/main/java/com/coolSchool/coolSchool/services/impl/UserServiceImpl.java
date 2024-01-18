package com.coolSchool.coolSchool.services.impl;

import com.coolSchool.coolSchool.enums.Provider;
import com.coolSchool.coolSchool.enums.Role;
import com.coolSchool.coolSchool.exceptions.blog.ValidationBlogException;
import com.coolSchool.coolSchool.exceptions.common.AccessDeniedException;
import com.coolSchool.coolSchool.exceptions.user.UserCreateException;
import com.coolSchool.coolSchool.exceptions.user.UserNotFoundException;
import com.coolSchool.coolSchool.models.dto.auth.AdminUserDTO;
import com.coolSchool.coolSchool.models.dto.auth.PublicUserDTO;
import com.coolSchool.coolSchool.models.dto.auth.RegisterRequest;
import com.coolSchool.coolSchool.models.dto.request.CompleteOAuthRequest;
import com.coolSchool.coolSchool.models.entity.User;
import com.coolSchool.coolSchool.repositories.UserRepository;
import com.coolSchool.coolSchool.security.CustomOAuth2User;
import com.coolSchool.coolSchool.services.UserService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public User createUser(RegisterRequest request) {
        Role roleFromReq = request.getRole();

        if (roleFromReq == null || roleFromReq.equals(Role.ADMIN)) {
            request.setRole(Role.USER);
        }

        try {
            User user = buildUser(request);
            return userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new UserCreateException(true);
        } catch (ConstraintViolationException exception) {
            throw new UserCreateException(exception.getConstraintViolations());
        }
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("email"));
    }

    @Override
    public List<AdminUserDTO> getAllUsers() {
        return userRepository
                .findAll()
                .stream()
                .map(x -> modelMapper.map(x, AdminUserDTO.class))
                .toList();
    }

    @Override
    public AdminUserDTO updateUser(Long id, AdminUserDTO userDTO, PublicUserDTO currentUser) {
        User userToUpdate = findById(id);

        if (userToUpdate.getId().equals(currentUser.getId())) {
            throw new AccessDeniedException();
        }

        modelMapper.map(userDTO, userToUpdate);
        userToUpdate.setId(id);

        try {
            User updatedUser = userRepository.save(userToUpdate);
            return modelMapper.map(updatedUser, AdminUserDTO.class);
        } catch (TransactionException exception) {
            if (exception.getRootCause() instanceof ConstraintViolationException validationException) {
                throw new ValidationBlogException(validationException.getConstraintViolations());
            }

            throw exception;
        }
    }


    @Override
    public void deleteUserById(Long id, PublicUserDTO currentUser) {
        User user = findById(id);

        if (user.getId().equals(currentUser.getId())) {
            throw new AccessDeniedException();
        }

        user.setDeleted(true);
        userRepository.save(user);
    }

    @Override
    public User processOAuthUser(CustomOAuth2User oAuth2User) {
        User user = userRepository.findByEmail(oAuth2User.getEmail()).orElse(null);

        if (user == null) {
            final String NAME_PLACEHOLDER = "CHANGE_NAME";
            final String DESCRIPTION_PLACEHOLDER = "CHANGE_THE_DESCRIPTION_PLEASE_CHANGE_THE_DESCRIPTION_PLEASEEE";
            final String ADDRESS_PLACEHOLDER = "CHANGE_ADDRESS";

            String username = oAuth2User.getName().toLowerCase()
                    .replaceAll("[^a-zA-Z0-9]", "");

            RegisterRequest registerRequest = new RegisterRequest();

            registerRequest.setEmail(oAuth2User.getEmail());
            registerRequest.setProvider(oAuth2User.getProvider());
            registerRequest.setUsername(username);
            registerRequest.setFirstname(NAME_PLACEHOLDER);
            registerRequest.setLastname(NAME_PLACEHOLDER);
            registerRequest.setRole(Role.USER);
            registerRequest.setDescription(DESCRIPTION_PLACEHOLDER);
            registerRequest.setAddress(ADDRESS_PLACEHOLDER);

            user = userRepository.save(buildUser(registerRequest));
        }

        return user;
    }

    @Override
    public User updateOAuth2UserWithFullData(CompleteOAuthRequest request, Long userId) {
        User user = findById(userId);
        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        user.setAddress(request.getAddress());
        user.setDescription(request.getDescription());
        user.setRole(request.getRole());
        user.setAdditionalInfoRequired(false);

        return userRepository.save(user);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("id"));
    }

    private User buildUser(RegisterRequest request) {
        boolean additionalInfoRequired = !request.getProvider().equals(Provider.LOCAL);

        User.UserBuilder userBuilder = User
                .builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .role(request.getRole())
                .provider(request.getProvider())
                .address(request.getAddress())
                .usernameField(request.getUsername())
                .description(request.getDescription())
                .additionalInfoRequired(additionalInfoRequired)
                .deleted(false);

        if (request.getPassword() != null) {
            userBuilder.password(passwordEncoder.encode(request.getPassword()));
        }

        return userBuilder.build();
    }
}
