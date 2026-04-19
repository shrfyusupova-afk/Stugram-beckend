package com.example.profile.routes

import com.example.profile.models.BaseResponse
import com.example.profile.service.MediaService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.mediaRoutes(service: MediaService) {
    authenticate("auth-jwt") {
        post("/upload") {
            val multipart = call.receiveMultipart()
            var fileUrl: String? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val file = File.createTempFile("upload-", part.originalFileName)
                    part.streamProvider().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    fileUrl = service.uploadFile(file, "profiles")
                    file.delete()
                }
                part.dispose()
            }

            if (fileUrl != null) {
                call.respond(BaseResponse(true, data = fileUrl))
            } else {
                call.respond(HttpStatusCode.InternalServerError, BaseResponse<Unit>(false, message = "Upload failed"))
            }
        }
    }
}
