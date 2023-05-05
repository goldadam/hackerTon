package com.example.forhackerton.controller;
/*
해당 문제가 있는 경우, 파일을 바로 Lambda에 파싱하는 API
 */

import com.example.forhackerton.common.BaseResponse;
import com.example.forhackerton.config.RegularResponseStatus;
import com.example.forhackerton.data.ChatGptResponseDto;
import com.example.forhackerton.data.QuestionRequestDto;
import com.example.forhackerton.service.ClovaService;
import com.example.forhackerton.service.CommonService;
import com.example.forhackerton.service.MyChatGptService;
import com.example.forhackerton.service.SendToLambdaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/ImageSend")
public class AskGPTWithImage {

    Logger logger = LoggerFactory.getLogger(AskGPTWithImage.class);

    private SendToLambdaService sendToLambdaService;
    private ClovaService clovaService;

    private MyChatGptService chatGptService;

    private CommonService commonService;

    public String beforeQuestion = "";

    @Autowired
    public AskGPTWithImage(SendToLambdaService sendToLambdaService, ClovaService clovaService, MyChatGptService chatGptService, CommonService commonService) {
        this.sendToLambdaService = sendToLambdaService;
        this.clovaService = clovaService;
        this.chatGptService = chatGptService;
        this.commonService = commonService;
    }

    /*
    Lambda 테스트 코드 -> 사용 X
     */
    @ResponseBody
    @PostMapping("/askWithImage")
    public BaseResponse askWithImage(@RequestParam("file")MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        try{
            byte[] bytes = file.getBytes();
            ChatGptResponseDto responseDto = sendToLambdaService.sendByteWithLambda(bytes);
            logger.info("service successfully transfered");
            logger.info("사진 데이터 Parsing");
            long endTime = System.currentTimeMillis();
            logger.info("총시간 : " + (endTime - startTime)/ 1000 + "." +  (endTime - startTime)%1000 + "초");
            return new BaseResponse<>(RegularResponseStatus.OK.getCode(), responseDto, RegularResponseStatus.OK.getMessage());
        }catch (Exception e){
            logger.info("Image data Parsing Error!!");
            e.printStackTrace();
            return new BaseResponse<>(RegularResponseStatus.SERVICE_UNAVAILABLE.getCode(), ChatGptResponseDto.builder().build(), RegularResponseStatus.SERVICE_UNAVAILABLE.getMessage());
        }
    }

    /*
    문제 사진이 있는 경우, 비슷한 유형의 문제 / 답 알려주는 API
     */
    @ResponseBody
    @PostMapping("/detect")
    public BaseResponse askColvaImage(@RequestParam("file")MultipartFile file) throws IOException{
        long startTime = System.currentTimeMillis();
        final String preq = "비슷한 유형의 문제 만들고, 답변 출력해줘. 형식은 문제 : , 답 : 이런식으로 ";
        try{
            String answer = clovaService.getClovaResponse(file);
            if(answer.isEmpty()){
                return new BaseResponse<>(RegularResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "ERROR", RegularResponseStatus.INTERNAL_SERVER_ERROR.getMessage());
            }
            ChatGptResponseDto responseDto = commonService.getCommonResponse(answer, preq);
            long endTime = System.currentTimeMillis();
            logger.info("총시간 : " + (endTime - startTime)/ 1000 + "." +  (endTime - startTime)%1000 + "초");
            return new BaseResponse<>(RegularResponseStatus.OK.getCode(), responseDto, RegularResponseStatus.OK.getMessage());
        }catch (Exception e){
            logger.info("Image Processing ERROR!");
            e.printStackTrace();
            return new BaseResponse<>(RegularResponseStatus.SERVICE_UNAVAILABLE.getCode(), ChatGptResponseDto.builder().build(), RegularResponseStatus.SERVICE_UNAVAILABLE.getMessage());
        }
    }

    /*
    문제 정답 출력 API
    ChatGPT를 두번 호출해, 문제 배열을 정렬시키고 해당하는 정답 출력함.
     */
    @ResponseBody
    @PostMapping("/justAnswer")
    public BaseResponse askWithImg(@RequestParam("file")MultipartFile file) throws IOException{
        logger.info("1");
        final String preq = "아래 문제의 정답만 정확히 얘기해줘.";
        logger.info("2");
        try{
            String answer = clovaService.getClovaResponse(file);
            logger.info("3");
            if(answer.isEmpty()){
                logger.info("clova ERROR!");
                return new BaseResponse<>(RegularResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "ERROR", RegularResponseStatus.INTERNAL_SERVER_ERROR.getMessage());
            }
            logger.info("4");
            ChatGptResponseDto responseDto = commonService.getCommonResponse(answer, preq);
            logger.info("5");
//          //정확도를 위해 2번 Request 진행
            ChatGptResponseDto correctResponseDto = commonService.getCommonResponse(responseDto.getChoices().toString(), preq);
            logger.info("6");
            return new BaseResponse<>(RegularResponseStatus.OK.getCode(), correctResponseDto, RegularResponseStatus.OK.getMessage());
        }catch (Exception e){
            logger.info("image Processing Error! with checking Wright Answer");
            e.printStackTrace();
            return new BaseResponse<>(RegularResponseStatus.SERVICE_UNAVAILABLE.getCode(), ChatGptResponseDto.builder().build(), RegularResponseStatus.SERVICE_UNAVAILABLE.getMessage());
        }
    }

    /*
    여러번 호출시 -> 서로 다른 문제 / 답 호출 가능
     */
    @ResponseBody
    @PostMapping("/withConcept")
    public BaseResponse askWithConceptWithImg(@RequestParam("file")MultipartFile file) throws IOException{
        final String preq = "해당 개념을 이용해 주관식 문제와 답 만들어줘. 이 개념과 다른 개념을 이용해서 만들면 좋아. 형식은 문제 : , 답 : 이런식으로 ";
        try{
            Boolean flag = false;
            String question = clovaService.getClovaResponse(file);
            if(question.isEmpty()){
                logger.info("clova ERROR!");
                return new BaseResponse<>(RegularResponseStatus.INTERNAL_SERVER_ERROR.getCode(), "ERROR", RegularResponseStatus.INTERNAL_SERVER_ERROR.getMessage());
            }
            if(flag){
                //다른 문제를 받아오고 싶을 때
                String condition = beforeQuestion + "과 다른 문제 출력해줘";
                ChatGptResponseDto responseDto = commonService.getCommonResponse( condition + question, preq);
                return new BaseResponse<>(RegularResponseStatus.OK.getCode(), responseDto, RegularResponseStatus.OK.getMessage());
            }else{
                ChatGptResponseDto responseDto = commonService.getCommonResponse(question, preq);
                beforeQuestion = responseDto.getChoices().toString();
                flag = true;
                return new BaseResponse<>(RegularResponseStatus.OK.getCode(), responseDto, RegularResponseStatus.OK.getMessage());
            }

        }catch (Exception e){
            e.printStackTrace();
            return new BaseResponse<>(RegularResponseStatus.SERVICE_UNAVAILABLE.getCode(), ChatGptResponseDto.builder().build(), RegularResponseStatus.SERVICE_UNAVAILABLE.getMessage());
        }
    }
}
