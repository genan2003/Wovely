package com.wovely.wovely.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.wovely.wovely.models.User;
import com.wovely.wovely.services.AddressService;
import com.wovely.wovely.support.SecuredWebMvcTest;
import com.wovely.wovely.support.TestTokens;

/** Address book: own entries only, validated, exactly one default, capped. */
@WebMvcTest(AddressController.class)
@Import(AddressService.class)
class AddressControllerTest extends SecuredWebMvcTest {

    static final String URL = "/api/user/addresses";
    static final String HOME = """
        {"label":"Home","recipientName":"Ana Pop","street":"Str. Florilor 3","city":"Sibiu",
         "postalCode":"550000","country":"Romania","phone":"+40 712 345 678"}""";

    @BeforeEach
    void saveReturnsUser() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ResultActions add(String token, String body) throws Exception {
        return mvc.perform(post(URL).header(AUTH, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String addId(String token, String body) throws Exception {
        return JsonPath.read(add(token, body).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void firstAddressBecomesDefaultAndListIsOwnOnly() throws Exception {
        add(TestTokens.buyer(), HOME)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.default").value(true));
        mvc.perform(get(URL).header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(URL).header(AUTH, bearer(TestTokens.otherBuyer())))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void exactlyOneDefault() throws Exception {
        String home = addId(TestTokens.buyer(), HOME);
        String work = addId(TestTokens.buyer(), HOME.replace("Home", "Work").replace("}", ",\"makeDefault\":true}"));
        var book = users.get("testuser").getAddresses();
        assertThat(book).filteredOn(a -> a.isDefault()).extracting(a -> a.getId()).containsExactly(work);

        mvc.perform(post(URL + "/" + home + "/default").header(AUTH, bearer(TestTokens.buyer())))
            .andExpect(status().isOk());
        assertThat(book).filteredOn(a -> a.isDefault()).extracting(a -> a.getId()).containsExactly(home);

        mvc.perform(delete(URL + "/" + home).header(AUTH, bearer(TestTokens.buyer()))).andExpect(status().isNoContent());
        assertThat(book).singleElement().satisfies(a -> assertThat(a.isDefault()).as("default moves on").isTrue());
    }

    @Test
    void updateReplacesFields() throws Exception {
        String id = addId(TestTokens.buyer(), HOME);
        mvc.perform(put(URL + "/" + id).header(AUTH, bearer(TestTokens.buyer())).contentType(MediaType.APPLICATION_JSON)
                .content(HOME.replace("Sibiu", "Cluj-Napoca")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.city").value("Cluj-Napoca"))
            .andExpect(jsonPath("$.default").value(true));
    }

    @Test
    void otherUsersAddressesAreNotFound() throws Exception {
        String id = addId(TestTokens.buyer(), HOME);
        mvc.perform(put(URL + "/" + id).header(AUTH, bearer(TestTokens.otherBuyer())).contentType(MediaType.APPLICATION_JSON)
                .content(HOME)).andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/" + id).header(AUTH, bearer(TestTokens.otherBuyer()))).andExpect(status().isNotFound());
        mvc.perform(post(URL + "/" + id + "/default").header(AUTH, bearer(TestTokens.otherBuyer()))).andExpect(status().isNotFound());
        assertThat(users.get("testuser").getAddresses()).hasSize(1);
    }

    @Test
    void validation() throws Exception {
        add(TestTokens.buyer(), HOME.replace("\"street\":\"Str. Florilor 3\",", "")).andExpect(status().isBadRequest());
        add(TestTokens.buyer(), HOME.replace("+40 712 345 678", "<script>")).andExpect(status().isBadRequest());
        add(TestTokens.buyer(), HOME.replace("550000", "55;00")).andExpect(status().isBadRequest());
        add(TestTokens.buyer(), HOME.replace("Ana Pop", "x".repeat(101))).andExpect(status().isBadRequest());
        verify(userRepository, never()).save(any());
    }

    @Test
    void capAtTen() throws Exception {
        for (int i = 0; i < AddressService.MAX_ADDRESSES; i++) {
            add(TestTokens.buyer(), HOME).andExpect(status().isCreated());
        }
        add(TestTokens.buyer(), HOME).andExpect(status().isConflict());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(HOME)).andExpect(status().isUnauthorized());
    }
}
