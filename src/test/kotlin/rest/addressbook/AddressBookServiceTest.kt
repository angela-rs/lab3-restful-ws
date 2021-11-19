package rest.addressbook

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.*
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.net.URI

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AddressBookServiceTest {

    @LocalServerPort
    var port = 0

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @BeforeEach
    fun cleanRepository() {
        addressBook.clear()
    }

    @Test
    fun serviceIsAlive() {
        // Request the address book
        val response = restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(0, response.body?.size)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////

        // Safe and idempotent: the server has the same elements it had before the GET. (none elements: 0)
        assertEquals(0, addressBook.personList.size)
    }

    @Test
    fun createUser() {

        // Prepare data
        val juan = Person(name = "Juan")
        val juanURI: URI = URI.create("http://localhost:$port/contacts/person/1")

        // Create a new user
        var response = restTemplate.postForEntity("http://localhost:$port/contacts", juan, Person::class.java)

        assertEquals(201, response.statusCode.value())
        assertEquals(juanURI, response.headers.location)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        var juanUpdated = response.body
        assertEquals(juan.name, juanUpdated?.name)
        assertEquals(1, juanUpdated?.id)
        assertEquals(juanURI, juanUpdated?.href)

        // Check that the new user exists
        response = restTemplate.getForEntity(juanURI, Person::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        juanUpdated = response.body
        assertEquals(juan.name, juanUpdated?.name)
        assertEquals(1, juanUpdated?.id)
        assertEquals(juanURI, juanUpdated?.href)

        //////////////////////////////////////////////////////////////////////
        // Verify that POST /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is not safe and not idempotent
        //////////////////////////////////////////////////////////////////////

        // Not safe: size has incremented by 1
        assertEquals(1, addressBook.personList.size)

        // Not idempotent: creating a new user again, response is different.
        val addressBookCopy = addressBook.personList.toMutableList()
        // Prepare data
        val juanURI2: URI = URI.create("http://localhost:$port/contacts/person/2")
        // Create a new user
        response = restTemplate.postForEntity("http://localhost:$port/contacts", juan, Person::class.java)
        assertEquals(201, response.statusCode.value())
        assertEquals(juanURI2, response.headers.location)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        val juanUpdated2 = response.body
        assertEquals(juan.name, juanUpdated2?.name)
        assertEquals(2, juanUpdated2?.id)
        assertEquals(juanURI2, juanUpdated2?.href)
        // Check location and body response has changed (idempotent)
        assertNotEquals(juanURI, response.headers.location)
        assertNotEquals(juanUpdated?.href, juanUpdated2?.href)
        assertNotEquals(juanUpdated2, juanUpdated)
        // Check it has been added
        assertNotEquals(addressBookCopy, addressBook)
        assert(addressBook.personList.contains(juanUpdated2))
    }

    @Test
    fun createUsers() {
        // Prepare server
        val salvador = Person(id = addressBook.nextId(), name = "Salvador")
        addressBook.personList.add(salvador)

        // Prepare data
        val juan = Person(name = "Juan")
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        val maria = Person(name = "Maria")
        val mariaURI = URI.create("http://localhost:$port/contacts/person/3")

        // Create a new user
        var response = restTemplate.postForEntity("http://localhost:$port/contacts", juan, Person::class.java)
        assertEquals(201, response.statusCode.value())
        assertEquals(juanURI, response.headers.location)

        // Create a second user
        response = restTemplate.postForEntity("http://localhost:$port/contacts", maria, Person::class.java)
        assertEquals(201, response.statusCode.value())
        assertEquals(mariaURI, response.headers.location)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)

        var mariaUpdated = response.body
        assertEquals(maria.name, mariaUpdated?.name)
        assertEquals(3, mariaUpdated?.id)
        assertEquals(mariaURI, mariaUpdated?.href)

        // Check that the new user exists
        response = restTemplate.getForEntity(mariaURI, Person::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        mariaUpdated = response.body
        assertEquals(maria.name, mariaUpdated?.name)
        assertEquals(3, mariaUpdated?.id)
        assertEquals(mariaURI, mariaUpdated?.href)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts/person/3 is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////

        // Safe: compare state before and after
        val addressBookCopy = addressBook.personList.toMutableList()
        val nextIdCopy = addressBook.nextId
        // Check again that the new user exists, GET
        val response2 = restTemplate.getForEntity(mariaURI, Person::class.java)
        assertEquals(200, response2.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response2.headers.contentType)
        // Check that the response, the addressBook and the nextId are the same as before
        assertEquals(response, response2)
        assertEquals(addressBookCopy, addressBook.personList)
        assertEquals(nextIdCopy, addressBook.nextId)
    }

    @Test
    fun listUsers() {

        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Test list of contacts
        val response = restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        assertEquals(2, response.body?.size)
        assertEquals(juan.name, response.body?.get(1)?.name)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////

        // Safe: compare state before and after and that it's idempotent
        val addresBookCopy = addressBook.personList.toMutableList();
        val nextIdCopy = addressBook.nextId
        assertEquals(addressBook.personList, response.body?.toMutableList())
        // Test again list of contacts, GET
        val response2 = restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        assertEquals(200, response2.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response2.headers.contentType)
        // Check that the response, the addressBook and the nextId are the same as before
        assertEquals(response, response2)
        assertEquals(addresBookCopy, addressBook.personList)
        assertEquals(nextIdCopy, addressBook.nextId)

    }

    @Test
    fun updateUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        //State before PUT
        val addressBookCopy = addressBook.personList.toMutableList()

        // Update Maria
        val maria = Person(name = "Maria")

        var response = restTemplate.exchange(juanURI, HttpMethod.PUT, HttpEntity(maria), Person::class.java)
        assertEquals(204, response.statusCode.value())
        val response1 = response

        // Verify that the update is real
        response = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        val updatedMaria = response.body
        assertEquals(maria.name, updatedMaria?.name)
        assertEquals(2, updatedMaria?.id)
        assertEquals(juanURI, updatedMaria?.href)

        // Verify that only can be updated existing values
        restTemplate.execute("http://localhost:$port/contacts/person/3", HttpMethod.PUT,
            {
                it.headers.contentType = MediaType.APPLICATION_JSON
                ObjectMapper().writeValue(it.body, maria)
            },
            { assertEquals(404, it.statusCode.value()) }
        )

        //////////////////////////////////////////////////////////////////////
        // Verify that PUT /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////
        
        // Idempotent: same petition will return the same answer
        // Update Maria again
        val response2 = restTemplate.exchange(juanURI, HttpMethod.PUT, HttpEntity(maria), Person::class.java)
        assertEquals(204, response2.statusCode.value())
        // Verify that the update is real
        response = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        val updatedMaria2 = response.body
        assertEquals(maria.name, updatedMaria?.name)
        assertEquals(2, updatedMaria?.id)
        assertEquals(juanURI, updatedMaria?.href)
        // Check the response is the same as before
        assertEquals(response1, response2)

        // Not safe: state of the server has changed from before the first petition to after this petition
        assertNotEquals(addressBook.personList, addressBookCopy)
        // Check only Maria has changed
        assertEquals(addressBook.personList.size, addressBookCopy.size)
        assert(addressBook.personList.contains(salvador))
        assertNotEquals(addressBook.personList.get(1), addressBookCopy.get(1)) 
        assertEquals(addressBook.personList.get(1), updatedMaria2)
    }

    @Test
    fun deleteUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // State before DELETE petition
        val addressBookCopy = addressBook.personList.toMutableList()
        val nextIdCopy = addressBook.nextId

        // Delete a user
        restTemplate.execute(juanURI, HttpMethod.DELETE, {}, { assertEquals(204, it.statusCode.value()) })

        // Verify that the user has been deleted
        restTemplate.execute(juanURI, HttpMethod.GET, {}, { assertEquals(404, it.statusCode.value()) })

        // State after PUT petition
        val nextState = ArrayList<Person>()
        for (p in addressBook.personList) {
            nextState.add(p.copy())
        }

        //////////////////////////////////////////////////////////////////////
        // Verify that DELETE /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////

        // Idempotent: repeat DELETE petition and receive same answer
        restTemplate.execute(juanURI, HttpMethod.DELETE, {}, { assertEquals(204, it.statusCode.value()) })

        // Not safe: status has changed after the first petition
        assertNotEquals(addressBookCopy, addressBook.personList)
        assert(addressBook.personList.contains(salvador))
        assertNotEquals(addressBookCopy.size, addressBook.personList.size)
        assertEquals(1, addressBook.personList.size)
        assertEquals(nextIdCopy, addressBook.nextId)
    }

    @Test
    fun findUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val salvadorURI = URI.create("http://localhost:$port/contacts/person/1")
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Test user 1 exists
        var response = restTemplate.getForEntity(salvadorURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        var person = response.body
        assertEquals(salvador.name, person?.name)
        assertEquals(salvador.id, person?.id)
        assertEquals(salvador.href, person?.href)

        // Test user 2 exists
        response = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        person = response.body
        assertEquals(juan.name, person?.name)
        assertEquals(juan.id, person?.id)
        assertEquals(juan.href, person?.href)

        // Test user 3 doesn't exist
        restTemplate.execute("http://localhost:$port/contacts/person/3", HttpMethod.GET, {}, { assertEquals(404, it.statusCode.value()) })
    }

}
