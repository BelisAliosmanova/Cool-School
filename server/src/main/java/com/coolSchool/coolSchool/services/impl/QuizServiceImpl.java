package com.coolSchool.coolSchool.services.impl;

import com.coolSchool.coolSchool.config.schedulers.QuizAttemptTimer;
import com.coolSchool.coolSchool.exceptions.course.CourseNotFoundException;
import com.coolSchool.coolSchool.exceptions.courseSubsection.CourseSubsectionNotFoundException;
import com.coolSchool.coolSchool.exceptions.quizzes.*;
import com.coolSchool.coolSchool.models.dto.auth.PublicUserDTO;
import com.coolSchool.coolSchool.models.dto.common.*;
import com.coolSchool.coolSchool.models.entity.*;
import com.coolSchool.coolSchool.repositories.*;
import com.coolSchool.coolSchool.services.AnswerService;
import com.coolSchool.coolSchool.services.QuestionService;
import com.coolSchool.coolSchool.services.QuizService;
import com.coolSchool.coolSchool.services.UserService;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class QuizServiceImpl implements QuizService {
    private final QuizRepository quizRepository;
    private final ModelMapper modelMapper;
    private final QuestionService questionService;
    private final AnswerService answerService;
    private final UserService userService;
    private final UserAnswerRepository userAnswerRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final CourseSubsectionRepository courseSubsectionRepository;
    private final CourseRepository courseRepository;
    private final UserQuizProgressRepository userQuizProgressRepository;
    private final MessageSource messageSource;
    private final QuizAttemptTimer quizAttemptTimer;

    public QuizServiceImpl(QuizRepository quizRepository, ModelMapper modelMapper, QuestionService questionService, AnswerService answerService, UserService userService, UserAnswerRepository userAnswerRepository, QuizAttemptRepository quizAttemptRepository, CourseSubsectionRepository courseSubsectionRepository, CourseRepository courseRepository, UserQuizProgressRepository userQuizProgressRepository, MessageSource messageSource, QuizAttemptTimer quizAttemptTimer) {
        this.quizRepository = quizRepository;
        this.modelMapper = modelMapper;
        this.questionService = questionService;
        this.answerService = answerService;
        this.userService = userService;
        this.userAnswerRepository = userAnswerRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.courseSubsectionRepository = courseSubsectionRepository;
        this.courseRepository = courseRepository;
        this.userQuizProgressRepository = userQuizProgressRepository;
        this.messageSource = messageSource;
        this.quizAttemptTimer = quizAttemptTimer;
    }

    @Override
    public List<QuizDTO> getAllQuizzes() {
        List<Quiz> quizzes = quizRepository.findByDeletedFalse();
        return quizzes.stream().map(quiz -> modelMapper.map(quiz, QuizDTO.class)).toList();
    }

    @Override
    public QuizDTO getQuizInfoById(Long id) {
        Quiz quiz = quizRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new QuizNotFoundException(messageSource));

        return modelMapper.map(quiz, QuizDTO.class);
    }

    @Override
    public QuizQuestionsAnswersDTO getQuizById(Long id, Long userId) {
        Quiz quiz = quizRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new QuizNotFoundException(messageSource));
        List<Question> questions = questionService.getQuestionsByQuizId(id);
        QuizDTO quizDTO = modelMapper.map(quiz, QuizDTO.class);

        AtomicReference<List<AnswerDTO>> filteredAnswers = new AtomicReference<>();

        boolean isUserTheCreatorOfQuiz = isTheUserQuizCreator(userId, quiz);

        List<QuestionAndAnswersDTO> questionAndAnswersList = questions.stream()
                .map(question -> {
                    List<AnswerDTO> answers = answerService.getAnswersByQuestionId(question.getId());

                    if (isUserTheCreatorOfQuiz) {
                        filteredAnswers.set(answers.stream()
                                .map(answer -> new AnswerDTO(answer.getId(), answer.getText(), answer.getQuestionId(), answer.isCorrect())).toList());
                    } else {
                        filteredAnswers.set(answers.stream()
                                .map(answer -> new AnswerDTO(answer.getId(), answer.getText(), answer.getQuestionId())).toList());
                    }

                    QuestionDTO questionDTO = modelMapper.map(question, QuestionDTO.class);
                    return new QuestionAndAnswersDTO(questionDTO, filteredAnswers.get());
                })
                .collect(Collectors.toList());

        List<UserQuizProgress> userQuizProgresses = userQuizProgressRepository.findByUserIdAndQuizId(userId, id);
        List<UserQuizProgressDTO> userQuizProgressDTOS = userQuizProgresses.stream()
                .map(userQuizProgress -> modelMapper.map(userQuizProgress, UserQuizProgressDTO.class)).toList();

        if (userQuizProgressDTOS.isEmpty()) {
            return new QuizQuestionsAnswersDTO(quizDTO, questionAndAnswersList);
        }
        return new QuizQuestionsAnswersDTO(quizDTO, questionAndAnswersList, userQuizProgressDTOS);
    }

    @Override
    public List<QuizDTO> getQuizzesBySubsectionId(Long subsectionId) {
        List<Quiz> quizzes = quizRepository.findBySubsectionIdAndDeletedFalse(subsectionId);
        return quizzes.stream()
                .map(quiz -> modelMapper.map(quiz, QuizDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public QuizDTO createQuiz(QuizDataDTO quizData) {
        QuizDTO quizDTO = quizData.getQuizDTO();
        List<QuestionAndAnswersDTO> questionAndAnswersList = quizData.getData();
        Quiz savedQuiz = quizRepository.save(modelMapper.map(quizDTO, Quiz.class));

        for (QuestionAndAnswersDTO questionAndAnswers : questionAndAnswersList) {
            QuestionDTO questionDTO = questionAndAnswers.getQuestion();
            questionDTO.setQuizId(savedQuiz.getId());
            QuestionDTO savedQuestion = questionService.createQuestion(questionDTO);
            for (AnswerDTO answerDTO : questionAndAnswers.getAnswers()) {
                answerDTO.setQuestionId(savedQuestion.getId());
                answerService.createAnswer(answerDTO);
            }
        }
        return modelMapper.map(savedQuiz, QuizDTO.class);
    }

    @Override
    public QuizDTO updateQuiz(Long quizId, QuizDataDTO updatedQuizData) {
        QuizDTO updatedQuizDTO = updatedQuizData.getQuizDTO();
        List<QuestionAndAnswersDTO> updatedQuestionAndAnswersList = updatedQuizData.getData();

        Quiz existingQuiz = quizRepository.findById(quizId).orElseThrow(() -> new QuizNotFoundException(messageSource));

        CourseSubsection courseSubsection = courseSubsectionRepository.findByIdAndDeletedFalse(updatedQuizDTO.getSubsectionId()).orElseThrow(() -> new CourseSubsectionNotFoundException(messageSource));

        existingQuiz.setTitle(updatedQuizDTO.getTitle());
        existingQuiz.setDescription(updatedQuizDTO.getDescription());
        existingQuiz.setStartTime(updatedQuizDTO.getStartTime());
        existingQuiz.setEndTime(updatedQuizDTO.getEndTime());
        existingQuiz.setSubsection(courseSubsection);
        existingQuiz.setAttemptLimit(updatedQuizDTO.getAttemptLimit());

        Quiz savedQuiz = quizRepository.save(existingQuiz);

        for (QuestionAndAnswersDTO updatedQnA : updatedQuestionAndAnswersList) {
            QuestionDTO updatedQuestionDTO = updatedQnA.getQuestion();
            updatedQuestionDTO.setQuizId(savedQuiz.getId());
            updatedQuestionDTO.setDescription(updatedQnA.getQuestion().getDescription());
            updatedQuestionDTO.setMarks(updatedQnA.getQuestion().getMarks());

            QuestionDTO savedQuestion = questionService.updateQuestion(updatedQuestionDTO.getId(), updatedQuestionDTO);

            for (AnswerDTO updatedAnswerDTO : updatedQnA.getAnswers()) {
                updatedAnswerDTO.setQuestionId(savedQuestion.getId());
                updatedAnswerDTO.setCorrect(updatedAnswerDTO.isCorrect());
                updatedAnswerDTO.setText(updatedAnswerDTO.getText());
                answerService.updateAnswer(updatedAnswerDTO.getId(), updatedAnswerDTO);
            }
        }
        return modelMapper.map(savedQuiz, QuizDTO.class);
    }


    @Override
    public void deleteQuiz(Long id) {
        Quiz quiz = quizRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new QuizNotFoundException(messageSource));
        List<Question> quizQuestions = questionService.getQuestionsByQuizId(id);

        for (Question question : quizQuestions) {
            for (AnswerDTO answerDTO : answerService.getAnswersByQuestionId(question.getId())) {
                Answer answer = modelMapper.map(answerDTO, Answer.class);
                answer.setDeleted(true);
                answerService.deleteAnswer(answer.getId());
            }
            question.setDeleted(true);
            questionService.deleteQuestion(question.getId());
        }

        quiz.setDeleted(true);
        quizRepository.save(quiz);
    }

    @Override
    public QuizResultDTO takeQuiz(Long quizId, List<UserAnswerDTO> userAnswers, Long userId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new QuizNotFoundException(messageSource));

        int quizDurationInMinutes = quiz.getQuizDurationInMinutes();
        LocalDateTime startTime = LocalDateTime.now();

        int attemptNumber = quizAttemptRepository.countByUserAndQuiz(userService.findById(userId), quiz) + 1;
        if (attemptNumber > quiz.getAttemptLimit()) {
            throw new NoMoreAttemptsQuizException(messageSource);
        }

        LocalDateTime currentTime = LocalDateTime.now();
        if (currentTime.isBefore(quiz.getStartTime()) || currentTime.isAfter(quiz.getEndTime())) {
            throw new QuizTimeNotValidException(messageSource);
        }

        QuizAttempt quizAttempt = new QuizAttempt();
        quizAttempt.setQuiz(quiz);
        quizAttempt.setUser(userService.findById(userId));
        quizAttempt.setAttemptNumber(attemptNumber);
        quizAttempt.setStartTime(LocalDateTime.now());
        quizAttempt = quizAttemptRepository.save(quizAttempt);
        BigDecimal totalMarks = BigDecimal.ZERO;

        for (UserAnswerDTO userAnswerDTO : userAnswers) {
            QuestionDTO question = questionService.getQuestionById(userAnswerDTO.getQuestionId());
            AnswerDTO answer = answerService.getAnswerById(userAnswerDTO.getSelectedOptionId());

            UserAnswer userAnswer = new UserAnswer();
            userAnswer.setQuestion(modelMapper.map(question, Question.class));
            userAnswer.setAnswer(modelMapper.map(answer, Answer.class));
            userAnswer.setQuizAttempt(quizAttempt);

            userAnswerRepository.save(userAnswer);

            List<AnswerDTO> correctAnswers = answerService.getCorrectAnswersByQuestionId(question.getId());
            boolean isCorrect = isUserAnswerCorrect(userAnswerDTO, correctAnswers);

            if (isCorrect) {
                totalMarks = totalMarks.add(question.getMarks());
            }
        }

        LocalDateTime endTime = LocalDateTime.now();
        long elapsedTimeInMinutes = Duration.between(startTime, endTime).toMinutes();
        if (elapsedTimeInMinutes > quizDurationInMinutes) {
            quizAttempt.setCompleted(true);
            throw new TimeLimitForQuizExceededException(messageSource);
        }

        quizAttempt.setTotalMarks(totalMarks);
        quizAttemptRepository.save(quizAttempt);

        return new QuizResultDTO(new QuizAttemptDTO(modelMapper.map(quiz, QuizDTO.class), modelMapper.map(quizAttempt.getUser(), PublicUserDTO.class),
                userAnswers, quizAttempt.getTotalMarks(), quizAttempt.getAttemptNumber(), 0L));

    }

    @Override
    public QuizAttemptDTO getQuizAttemptDetails(Long quizAttemptId) {
        QuizAttempt quizAttempt = quizAttemptRepository.findById(quizAttemptId)
                .orElseThrow(() -> new QuizAttemptNotFoundException(messageSource));

        long timeLeft = calculateTimeLeftForQuizAttempt(quizAttempt.getId(), quizAttempt.getQuiz().getQuizDurationInMinutes());

        QuizAttemptDTO quizAttemptDTO = modelMapper.map(quizAttempt, QuizAttemptDTO.class);
        quizAttemptDTO.setTimeLeft(timeLeft);

        return quizAttemptDTO;
    }
    @Override
    public List<QuizAttemptDTO> getAllUserAttemptsInAQuiz(Long quizId, PublicUserDTO publicUserDTO) {
        List<QuizAttempt> quizAttempts = quizAttemptRepository.findByQuizIdAndUserId(quizId, publicUserDTO.getId());

        return quizAttempts.stream()
                .map(quizAttempt -> modelMapper.map(quizAttempt, QuizAttemptDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserQuizProgressDTO> autoSaveUserProgress(Long quizId, Long questionId, Long answerId, Long userId, Long quizAttemptId) {
        UserQuizProgressDTO userQuizProgressDTO = new UserQuizProgressDTO();
        QuizAttempt quizAttempt = quizAttemptRepository.findById(quizAttemptId).orElseThrow(() -> new QuizAttemptNotFoundException(messageSource));
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new QuizNotFoundException(messageSource));
        userQuizProgressDTO.setUserId(userId);
        userQuizProgressDTO.setQuizId(quizId);
        userQuizProgressDTO.setAnswerId(answerId);
        userQuizProgressDTO.setQuestionId(questionId);
        quizAttempt.setTimeLeft(quiz.getQuizDurationInMinutes());
        quizAttemptTimer.updateQuizAttemptsTimeLeft();
        if (quizAttempt.getTimeLeft() <= 0) {
            throw new TimeLimitForQuizExceededException(messageSource);
        }
        UserQuizProgress userQuizProgress = modelMapper.map(userQuizProgressDTO, UserQuizProgress.class);
        userQuizProgressRepository.save(userQuizProgress);

        return getAllUserProgressForQuiz(quizId);
    }

    @Override
    @Transactional
    public void deleteAutoSavedProgress(Long userId, Long quizId) {
        userQuizProgressRepository.deleteByUserIdAndQuizId(userId, quizId);
    }

    public long calculateTimeLeftForQuizAttempt(Long quizAttemptId, int quizDurationInMinutes) {
        QuizAttempt quizAttempt = quizAttemptRepository.findById(quizAttemptId)
                .orElseThrow(() -> new QuizAttemptNotFoundException(messageSource));
        LocalDateTime startTime = quizAttempt.getStartTime();
        LocalDateTime currentTime = LocalDateTime.now();
        long elapsedTimeInMinutes = Duration.between(startTime, currentTime).toMinutes();
        long timeLeft = quizDurationInMinutes - elapsedTimeInMinutes;
        return Math.max(timeLeft, 0);
    }

    private List<UserQuizProgressDTO> getAllUserProgressForQuiz(Long quizId) {
        List<UserQuizProgress> userQuizProgressList = userQuizProgressRepository.findByQuizId(quizId);
        return userQuizProgressList.stream()
                .map(progress -> modelMapper.map(progress, UserQuizProgressDTO.class))
                .collect(Collectors.toList());
    }

    private boolean isUserAnswerCorrect(UserAnswerDTO userAnswer, List<AnswerDTO> correctAnswers) {
        if (userAnswer == null) {
            return false;
        }

        Long userSelectedOptionId = userAnswer.getSelectedOptionId();

        if (userSelectedOptionId == null) {
            return false;
        }

        return correctAnswers.stream()
                .anyMatch(correctAnswer -> correctAnswer.getId().equals(userSelectedOptionId));
    }

    private boolean isTheUserQuizCreator(Long userId, Quiz quiz) {
        CourseSubsection courseSubsection = courseSubsectionRepository.findByIdAndDeletedFalse(quiz.getSubsection().getId()).orElseThrow(() -> new CourseSubsectionNotFoundException(messageSource));
        Course course = courseRepository.findByIdAndDeletedFalse(courseSubsection.getCourse().getId()).orElseThrow(() -> new CourseNotFoundException(messageSource));
        return Objects.equals(course.getUser().getId(), userId);
    }
}
